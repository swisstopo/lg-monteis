import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, resource, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { PermissionsService } from '@core/auth/permissions.service';
import { toErrorDtos } from '@core/http/api-error.model';
import { ToastService } from '@core/notifications/toast.service';
import { FulcrumDocumentService } from '@features/fulcrum-document/services/fulcrum-document.service';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

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
  protected readonly permissions = inject(PermissionsService);

  protected readonly status = resource({
    loader: () => this.documentService.getSyncStatus(),
  });
  protected readonly starting = signal(false);

  async onRefresh(): Promise<void> {
    this.starting.set(true);
    try {
      await this.documentService.startSync();
      this.toastService.success(this.translateService.translate('fulcrumSync.started')());
    } catch (error) {
      if (isAlreadyRunning(error)) {
        this.toastService.warning(this.translateService.translate(ALREADY_RUNNING)());
      } else {
        this.toastStartErrors(error);
      }
    } finally {
      this.starting.set(false);
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
