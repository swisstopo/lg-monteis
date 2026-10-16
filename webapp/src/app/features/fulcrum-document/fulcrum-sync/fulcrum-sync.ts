import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  InjectionToken,
  inject,
  output,
  signal,
} from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { PermissionsService } from '@core/auth/permissions.service';
import { FulcrumSyncStatusResponseDto } from '@core/generated';
import { toErrorDtos } from '@core/http/api-error.model';
import { ToastService } from '@core/notifications/toast.service';
import { FulcrumDocumentService } from '@features/fulcrum-document/services/fulcrum-document.service';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

export const SYNC_POLL_INTERVAL_MS = new InjectionToken<number>('SYNC_POLL_INTERVAL_MS', {
  factory: () => 5_000,
});

const ALREADY_RUNNING = 'fulcrum.sync.alreadyRunning';

@Component({
  selector: 'app-fulcrum-sync',
  imports: [DatePipe, MatButton, MatIcon, MatProgressSpinner, TranslatePipe],
  templateUrl: './fulcrum-sync.html',
  styleUrl: './fulcrum-sync.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FulcrumSync {
  private readonly documentService = inject(FulcrumDocumentService);
  private readonly toastService = inject(ToastService);
  private readonly translateService = inject(TranslateService);
  private readonly pollInterval = inject(SYNC_POLL_INTERVAL_MS);
  protected readonly permissions = inject(PermissionsService);

  /** A sync this view watched has ended, the Fulcrum Documents changed. */
  readonly finished = output<void>();

  protected readonly status = signal<FulcrumSyncStatusResponseDto | undefined>(undefined);
  protected readonly running = signal(false);
  protected readonly statusUnknown = signal(false);

  private destroyed = false;

  constructor() {
    inject(DestroyRef).onDestroy(() => (this.destroyed = true));
    void this.loadStatus();
  }

  async onRefresh(): Promise<void> {
    this.running.set(true);
    try {
      this.status.set(await this.documentService.startSync());
    } catch (error) {
      if (!isAlreadyRunning(error)) {
        this.running.set(false);
        this.toastStartErrors(error);
        return;
      }
    }
    await this.pollUntilFinished();
  }

  // a 403 for a user without document access just leaves the status out
  private async loadStatus(): Promise<void> {
    try {
      const status = await this.documentService.getSyncStatus();
      this.status.set(status);
      if (status.running) {
        this.running.set(true);
        await this.pollUntilFinished();
      }
    } catch {
      this.status.set(undefined);
    }
  }

  private async pollUntilFinished(): Promise<void> {
    this.statusUnknown.set(false);
    while (!this.destroyed) {
      await new Promise((resolve) => setTimeout(resolve, this.pollInterval));
      if (this.destroyed) return;

      let status: FulcrumSyncStatusResponseDto;
      try {
        status = await this.documentService.getSyncStatus();
      } catch {
        this.running.set(false);
        this.statusUnknown.set(true);
        return;
      }
      this.status.set(status);
      if (!status.running) {
        this.running.set(false);
        this.finished.emit();
        return;
      }
    }
  }

  private toastStartErrors(error: unknown): void {
    const messages = toErrorDtos(error)
      .filter((dto) => !!dto.messageKey)
      .map((dto) => this.translateService.translate(dto.messageKey!, dto.params)());
    if (messages.length === 0) {
      messages.push(this.translateService.translate('fulcrumSync.error.start')());
    }
    messages.forEach((message) => this.toastService.error(message));
  }
}

function isAlreadyRunning(error: unknown): boolean {
  return toErrorDtos(error).some((dto) => dto.messageKey === ALREADY_RUNNING);
}
