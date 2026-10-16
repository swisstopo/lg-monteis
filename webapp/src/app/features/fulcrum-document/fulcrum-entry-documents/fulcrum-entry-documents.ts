import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatMenu, MatMenuContent, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { MatTooltip } from '@angular/material/tooltip';
import { PermissionsService } from '@core/auth/permissions.service';
import { FulcrumDocumentResponseDto, FulcrumEntryResponseDto } from '@core/generated';
import { ToastService } from '@core/notifications/toast.service';
import { FulcrumDocumentService } from '@features/fulcrum-document/services/fulcrum-document.service';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { FileDownloadService } from '@ui/file-download/file-download.service';
import { ICellRendererAngularComp } from 'ag-grid-angular';
import { ICellRendererParams } from 'ag-grid-community';

@Component({
  selector: 'app-fulcrum-entry-documents',
  templateUrl: './fulcrum-entry-documents.html',
  styleUrl: './fulcrum-entry-documents.scss',
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
export class FulcrumEntryDocuments implements ICellRendererAngularComp {
  private readonly documentService = inject(FulcrumDocumentService);
  private readonly fileDownloadService = inject(FileDownloadService);
  private readonly toastService = inject(ToastService);
  private readonly translateService = inject(TranslateService);
  protected readonly permissions = inject(PermissionsService);

  private readonly entry = signal<FulcrumEntryResponseDto | undefined>(undefined);
  protected readonly documents = computed(() => this.entry()?.documents ?? []);

  // the documents of a multi-document entry carry their own marker in the menu, the cell only hints
  protected readonly removedKey = computed(() => {
    const entry = this.entry();
    if (entry && !entry.availableInFulcrum) return 'fulcrumEntry.removed.entry';
    const removed = this.documents().filter((document) => !document.availableInFulcrum);
    if (removed.length === 0) return undefined;
    return this.documents().length === 1
      ? 'fulcrumEntry.removed.document'
      : 'fulcrumEntry.removed.someDocuments';
  });

  agInit(params: ICellRendererParams<FulcrumEntryResponseDto>): void {
    this.refresh(params);
  }

  refresh(params: ICellRendererParams<FulcrumEntryResponseDto>): boolean {
    this.entry.set(params.data);
    return true;
  }

  protected view(document: FulcrumDocumentResponseDto): Promise<void> {
    return this.toastOnFailure(() =>
      this.fileDownloadService.openInNewTab(() => this.documentService.getContent(document.id)),
    );
  }

  protected download(document: FulcrumDocumentResponseDto): Promise<void> {
    return this.toastOnFailure(async () =>
      this.fileDownloadService.download(
        await this.documentService.getContent(document.id),
        document.fileName,
      ),
    );
  }

  private async toastOnFailure(action: () => Promise<void>): Promise<void> {
    try {
      await action();
    } catch {
      this.toastService.error(
        this.translateService.translate('fulcrumEntry.documents.error.download')(),
      );
    }
  }
}
