import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  InjectionToken,
  computed,
  inject,
  output,
  signal,
} from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { PermissionsService } from '@core/auth/permissions.service';
import { FulcrumSyncRunResponseDto } from '@core/generated';
import { toErrorDtos } from '@core/http/api-error.model';
import { ToastService } from '@core/notifications/toast.service';
import { FulcrumDocumentService } from '@features/fulcrum-document/services/fulcrum-document.service';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import StatusEnum = FulcrumSyncRunResponseDto.StatusEnum;

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

  /** A run this view watched has ended, the Fulcrum entries changed. */
  readonly finished = output<void>();

  protected readonly latestRun = signal<FulcrumSyncRunResponseDto | undefined>(undefined);
  protected readonly running = signal(false);
  protected readonly statusUnknown = signal(false);
  protected readonly lastRunHintKey = computed(() => {
    switch (this.latestRun()?.status) {
      case StatusEnum.Failed:
        return 'fulcrumSync.failed';
      case StatusEnum.Incomplete:
        return 'fulcrumSync.incomplete';
      default:
        return undefined;
    }
  });

  private destroyed = false;

  constructor() {
    inject(DestroyRef).onDestroy(() => (this.destroyed = true));
    void this.loadLatestRun();
  }

  async onRefresh(): Promise<void> {
    this.running.set(true);
    try {
      this.latestRun.set(await this.documentService.startSync());
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
  private async loadLatestRun(): Promise<void> {
    try {
      const run = await this.documentService.getLatestSyncRun();
      this.latestRun.set(run);
      if (run?.status === StatusEnum.Running) {
        this.running.set(true);
        await this.pollUntilFinished();
      }
    } catch {
      this.latestRun.set(undefined);
    }
  }

  private async pollUntilFinished(): Promise<void> {
    this.statusUnknown.set(false);
    while (!this.destroyed) {
      await new Promise((resolve) => setTimeout(resolve, this.pollInterval));
      if (this.destroyed) return;

      let run: FulcrumSyncRunResponseDto | undefined;
      try {
        run = await this.documentService.getLatestSyncRun();
      } catch {
        this.running.set(false);
        this.statusUnknown.set(true);
        return;
      }
      this.latestRun.set(run);
      if (run?.status !== StatusEnum.Running) {
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
