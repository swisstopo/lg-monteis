import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  resource,
  signal,
} from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { PermissionsService } from '@core/auth/permissions.service';
import { FulcrumDocumentResponseDto } from '@core/generated';
import { ToastService } from '@core/notifications/toast.service';
import { FulcrumSync } from '@features/fulcrum-document/fulcrum-sync/fulcrum-sync';
import { FulcrumDocumentService } from '@features/fulcrum-document/services/fulcrum-document.service';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { WorkbenchView } from '@scion/workbench';
import { FileDownloadService } from '@ui/file-download/file-download.service';
import { InlineError } from '@ui/inline-error/inline-error';
import { TableHeader } from '@ui/table-header/table-header';
import Table from '@ui/table/table';
import { GridOptions } from 'ag-grid-community';
import { createColumns } from './columns';
import { matchesSearch } from './search';

@Component({
  selector: 'app-fulcrum-document-table',
  imports: [TableHeader, TranslatePipe, Table, InlineError, FulcrumSync, MatButton, MatIcon],
  templateUrl: './fulcrum-document-table.html',
  styleUrl: './fulcrum-document-table.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class FulcrumDocumentTable {
  private readonly documentService = inject(FulcrumDocumentService);
  private readonly fileDownloadService = inject(FileDownloadService);
  private readonly toastService = inject(ToastService);
  private readonly translateService = inject(TranslateService);
  protected readonly permissions = inject(PermissionsService);

  readonly searchTerm = signal<string>('');

  protected readonly selectedDocument = signal<FulcrumDocumentResponseDto | undefined>(undefined);

  protected readonly columns = createColumns();

  protected readonly documents = resource({
    loader: () => this.documentService.getDocuments(),
  });

  // undefined while loading, so the grid shows its loading overlay instead of "no rows"
  protected readonly visibleRows = computed(() => {
    if (!this.documents.hasValue()) return undefined;
    const term = this.searchTerm();
    return this.documents.value().filter((document) => matchesSearch(document, term));
  });

  // without document access there is no action for a selected row, so a click selects nothing
  protected readonly gridOptions = computed<GridOptions<FulcrumDocumentResponseDto>>(() => ({
    domLayout: 'normal',
    ...(this.permissions.canAccessDocuments() ? {} : { rowSelection: undefined }),
  }));

  constructor(view: WorkbenchView) {
    effect(() => {
      view.title = this.translateService.translate('tab.fulcrumDocument')();
    });
  }

  onSearch(term: string): void {
    this.searchTerm.set(term);
  }

  onSelectionChanged(rows: FulcrumDocumentResponseDto[]): void {
    this.selectedDocument.set(rows[0]);
  }

  onView(): Promise<void> {
    const document = this.selectedDocument();
    if (!document) return Promise.resolve();
    return this.toastOnFailure(() =>
      this.fileDownloadService.openInNewTab(() => this.documentService.getContent(document.id)),
    );
  }

  onDownload(): Promise<void> {
    const document = this.selectedDocument();
    if (!document) return Promise.resolve();
    return this.toastOnFailure(async () =>
      this.fileDownloadService.download(
        await this.documentService.getContent(document.id),
        document.file.fileName,
      ),
    );
  }

  private async toastOnFailure(action: () => Promise<void>): Promise<void> {
    try {
      await action();
    } catch {
      this.toastService.error(
        this.translateService.translate('fulcrumDocument.file.error.download')(),
      );
    }
  }

  protected getRowId = (row: FulcrumDocumentResponseDto): string => row.id;
}
