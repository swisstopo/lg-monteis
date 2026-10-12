import { DatePipe } from '@angular/common';
import { Component, effect, inject, inputBinding, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { OrganisationResponseDto } from '@core/generated';
import { ToastService } from '@core/notifications/toast.service';
import OrganisationEdit from '@features/admin/organisatoin/organisation-edit/organisation-edit';
import { createColumns } from '@features/admin/organisatoin/organisation-table/columns';
import { OrganisationService } from '@features/admin/organisatoin/services/organisation.service';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { WorkbenchView } from '@scion/workbench';
import { InlineError } from '@ui/inline-error/inline-error';
import { TableHeader } from '@ui/table-header/table-header';
import { FileDownloadService } from '@ui/file-download/file-download.service';
import { createPagedDatasource } from '@ui/table/paged-datasource.factory';
import { toGridFilterSortParams } from '@ui/table/paged-request.mapper';
import Table from '@ui/table/table';
import { GridApi, GridOptions } from 'ag-grid-community';

@Component({
  selector: 'app-organisation-table',
  imports: [TableHeader, MatButton, MatIcon, MatProgressSpinner, TranslatePipe, Table, InlineError],
  providers: [DatePipe],
  templateUrl: './organisation-table.html',
  styleUrl: './organisation-table.scss',
})
export default class OrganisationTable {
  private readonly dialog = inject(MatDialog);
  protected organisationService = inject(OrganisationService);
  private readonly translateService = inject(TranslateService);
  private readonly csvDownloadService = inject(FileDownloadService);
  private readonly toastService = inject(ToastService);

  readonly searchTerm = signal<string>('');

  protected wrappedCols = createColumns();
  protected selectedOrganisationId = signal<string | undefined>(undefined);
  private selectedOrganisationName: string | undefined;
  protected totalCount = signal<number | undefined>(undefined);
  protected loadError = signal(false);
  protected downloading = signal(false);
  private readonly gridApi = signal<GridApi | undefined>(undefined);

  protected gridOptions: GridOptions<OrganisationResponseDto> = {
    domLayout: 'normal',
    autoSizeStrategy: {
      type: 'fitCellContents',
      scaleUpToFitGridWidth: true,
      // Without this, the scale-up only runs once on first render, so toggling the sidenav
      // (which resizes the grid's container) leaves the columns sized for the old width.
      // This is needed because the organisation has too little columns once they get more we can transform this
      // to the same logic as the other tables in this application.
      continuous: true,
    },
  };

  protected datasource = createPagedDatasource(
    (params) => this.organisationService.getOrganisations(params),
    this.totalCount,
    this.loadError,
  );

  constructor(view: WorkbenchView) {
    // SCION Workbench: Dynamically update the tab title whenever the data changes
    effect(() => {
      view.title = this.translateService.translate('tab.organisation')();
    });

    // Re-fetch the currently visible pages whenever a organisation is created/updated elsewhere
    // (e.g. via the edit dialog) - the infinite row model otherwise has no way to know.
    effect(() => {
      if (this.organisationService.organisationsChanged()) {
        this.gridApi()?.refreshInfiniteCache();
        this.organisationService.organisationsChanged.set(false);
      }
    });
  }

  onGridReady(api: GridApi): void {
    this.gridApi.set(api);
  }

  onWrappedRow(row: OrganisationResponseDto) {
    console.log(row);
  }

  onSelectionChanged(rows: OrganisationResponseDto[]): void {
    this.selectedOrganisationId.set(rows[0]?.id);
    this.selectedOrganisationName = rows[0]?.name;
  }

  onCreate(): void {
    this.dialog.open(OrganisationEdit, { width: '40vw', maxWidth: '600px', autoFocus: true });
  }

  onEdit(): void {
    const organisationId = this.selectedOrganisationId();
    if (organisationId === undefined) return;

    this.dialog.open(OrganisationEdit, {
      width: '40vw',
      maxWidth: '600px',
      autoFocus: true,
      bindings: [inputBinding('organisationId', () => organisationId)],
    });
  }

  async onDownload(): Promise<void> {
    const api = this.gridApi();
    if (!api) return;

    this.downloading.set(true);
    try {
      const { sortModel, filterModel } = toGridFilterSortParams(api);
      const blob = await this.organisationService.getOrganisationsCsv(sortModel, filterModel);
      this.csvDownloadService.download(blob, 'organisations.csv');
    } catch {
      this.toastService.error(
        this.translateService.translate('organisation.error.downloadFailed')(),
      );
    } finally {
      this.downloading.set(false);
    }
  }

  onSearch(term: string): void {
    this.searchTerm.set(term);
    // Infinite row model has no quick filter, so the search box drives the name column filter.
    const api = this.gridApi();
    if (!api) return;
    const trimmed = term.trim();
    api.setFilterModel(
      trimmed ? { name: { filterType: 'text', type: 'contains', filter: trimmed } } : null,
    );
  }

  protected getOrganisationRowId = (row: OrganisationResponseDto): string => String(row.id);
}
