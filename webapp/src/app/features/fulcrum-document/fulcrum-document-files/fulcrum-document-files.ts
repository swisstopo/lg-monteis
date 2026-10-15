import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatMenu, MatMenuContent, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { MatTooltip } from '@angular/material/tooltip';
import { PermissionsService } from '@core/auth/permissions.service';
import { FulcrumDocumentFileResponseDto, FulcrumDocumentResponseDto } from '@core/generated';
import { ToastService } from '@core/notifications/toast.service';
import { FulcrumDocumentService } from '@features/fulcrum-document/services/fulcrum-document.service';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { FileDownloadService } from '@ui/file-download/file-download.service';
import { ICellRendererAngularComp } from 'ag-grid-angular';
import { ICellRendererParams } from 'ag-grid-community';

@Component({
  selector: 'app-fulcrum-document-files',
  templateUrl: './fulcrum-document-files.html',
  styleUrl: './fulcrum-document-files.scss',
  imports: [
    MatIconButton,
    MatIcon,
    MatMenu,
    MatMenuContent,
    MatMenuItem,
    MatMenuTrigger,
    MatTooltip,
    TranslatePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FulcrumDocumentFiles implements ICellRendererAngularComp {
  private readonly documentService = inject(FulcrumDocumentService);
  private readonly fileDownloadService = inject(FileDownloadService);
  private readonly toastService = inject(ToastService);
  private readonly translateService = inject(TranslateService);
  protected readonly permissions = inject(PermissionsService);

  private readonly document = signal<FulcrumDocumentResponseDto | undefined>(undefined);
  protected readonly files = computed(() => this.document()?.files ?? []);

  // the files of a multi-file document carry their own marker in the menu, the cell only hints
  protected readonly removedKey = computed(() => {
    const document = this.document();
    if (document && !document.availableInFulcrum) return 'fulcrumDocument.removed.document';
    const removedFiles = this.files().filter((file) => !file.availableInFulcrum);
    if (removedFiles.length === 0) return undefined;
    return this.files().length === 1
      ? 'fulcrumDocument.removed.file'
      : 'fulcrumDocument.removed.someFiles';
  });

  agInit(params: ICellRendererParams<FulcrumDocumentResponseDto>): void {
    this.refresh(params);
  }

  refresh(params: ICellRendererParams<FulcrumDocumentResponseDto>): boolean {
    this.document.set(params.data);
    return true;
  }

  protected view(file: FulcrumDocumentFileResponseDto): Promise<void> {
    return this.toastOnFailure(() =>
      this.fileDownloadService.openInNewTab(() => this.documentService.getContent(file.id)),
    );
  }

  protected download(file: FulcrumDocumentFileResponseDto): Promise<void> {
    return this.toastOnFailure(async () =>
      this.fileDownloadService.download(
        await this.documentService.getContent(file.id),
        file.fileName,
      ),
    );
  }

  private async toastOnFailure(action: () => Promise<void>): Promise<void> {
    try {
      await action();
    } catch {
      this.toastService.error(
        this.translateService.translate('fulcrumDocument.files.error.download')(),
      );
    }
  }
}
