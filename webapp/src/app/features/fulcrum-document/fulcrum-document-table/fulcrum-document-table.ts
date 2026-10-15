import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  resource,
  signal,
} from '@angular/core';
import { FulcrumDocumentResponseDto } from '@core/generated';
import { FulcrumSync } from '@features/fulcrum-document/fulcrum-sync/fulcrum-sync';
import { FulcrumDocumentService } from '@features/fulcrum-document/services/fulcrum-document.service';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { WorkbenchView } from '@scion/workbench';
import { InlineError } from '@ui/inline-error/inline-error';
import { TableHeader } from '@ui/table-header/table-header';
import Table from '@ui/table/table';
import { GridOptions } from 'ag-grid-community';
import { createColumns } from './columns';
import { matchesSearch } from './search';

@Component({
  selector: 'app-fulcrum-document-table',
  imports: [TableHeader, TranslatePipe, Table, InlineError, FulcrumSync],
  templateUrl: './fulcrum-document-table.html',
  styleUrl: './fulcrum-document-table.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class FulcrumDocumentTable {
  private readonly documentService = inject(FulcrumDocumentService);
  private readonly translateService = inject(TranslateService);

  readonly searchTerm = signal<string>('');

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

  protected readonly gridOptions: GridOptions<FulcrumDocumentResponseDto> = {
    domLayout: 'normal',
    // read only, a click on a file button should not select the row
    rowSelection: undefined,
  };

  constructor(view: WorkbenchView) {
    effect(() => {
      view.title = this.translateService.translate('tab.fulcrumDocument')();
    });
  }

  onSearch(term: string): void {
    this.searchTerm.set(term);
  }

  onSyncFinished(): void {
    this.documents.reload();
  }

  protected getRowId = (row: FulcrumDocumentResponseDto): string => row.id;
}
