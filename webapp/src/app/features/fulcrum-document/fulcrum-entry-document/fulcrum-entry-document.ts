import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { PermissionsService } from '@core/auth/permissions.service';
import { FulcrumEntryResponseDto } from '@core/generated';
import { ToastService } from '@core/notifications/toast.service';
import { FulcrumDocumentService } from '@features/fulcrum-document/services/fulcrum-document.service';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { FileDownloadService } from '@ui/file-download/file-download.service';
import { ICellRendererAngularComp } from 'ag-grid-angular';
import { ICellRendererParams } from 'ag-grid-community';

@Component({
  selector: 'app-fulcrum-entry-document',
  templateUrl: './fulcrum-entry-document.html',
  styleUrl: './fulcrum-entry-document.scss',
  imports: [MatIconButton, MatIcon, MatTooltip, TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FulcrumEntryDocument implements ICellRendererAngularComp {
  private readonly documentService = inject(FulcrumDocumentService);
  private readonly fileDownloadService = inject(FileDownloadService);
  private readonly toastService = inject(ToastService);
  private readonly translateService = inject(TranslateService);
  protected readonly permissions = inject(PermissionsService);

  protected readonly entry = signal<FulcrumEntryResponseDto | undefined>(undefined);

  agInit(params: ICellRendererParams<FulcrumEntryResponseDto>): void {
    this.refresh(params);
  }

  refresh(params: ICellRendererParams<FulcrumEntryResponseDto>): boolean {
    this.entry.set(params.data);
    return true;
  }

  protected view(entry: FulcrumEntryResponseDto): Promise<void> {
    return this.toastOnFailure(() =>
      this.fileDownloadService.openInNewTab(() =>
        this.documentService.getContent(entry.document.id),
      ),
    );
  }

  protected download(entry: FulcrumEntryResponseDto): Promise<void> {
    return this.toastOnFailure(async () =>
      this.fileDownloadService.download(
        await this.documentService.getContent(entry.document.id),
        entry.document.fileName,
      ),
    );
  }

  private async toastOnFailure(action: () => Promise<void>): Promise<void> {
    try {
      await action();
    } catch {
      this.toastService.error(
        this.translateService.translate('fulcrumEntry.document.error.download')(),
      );
    }
  }
}
