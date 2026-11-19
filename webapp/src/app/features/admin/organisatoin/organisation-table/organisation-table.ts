import {
  ChangeDetectionStrategy,
  Component,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { OrganisationResponseDto } from '@core/generated';
import OrganisationEdit from '@features/admin/organisatoin/organisation-edit/organisation-edit';
import { createColumns } from '@features/admin/organisatoin/organisation-table/columns';
import { OrganisationService } from '@features/admin/organisatoin/services/organisation.service';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { WorkbenchView } from '@scion/workbench';
import { FormDialogService } from '@ui/dialog/form-dialog.service';
import { InlineError } from '@ui/inline-error/inline-error';
import { TableHeader } from '@ui/table-header/table-header';
import { createCsvDownload } from '@ui/table/csv-download';
import { createPagedDatasource } from '@ui/table/paged-datasource.factory';
import Table from '@ui/table/table';
import { GridApi, GridOptions } from 'ag-grid-community';

@Component({
  selector: 'app-organisation-table',
  imports: [TableHeader, MatButton, MatIcon, MatProgressSpinner, TranslatePipe, Table, InlineError],
  templateUrl: './organisation-table.html',
  styleUrl: './organisation-table.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class OrganisationTable {
  private readonly formDialog = inject(FormDialogService);
  private readonly organisationService = inject(OrganisationService);
  private readonly translateService = inject(TranslateService);

  protected readonly columns = createColumns();
  protected readonly selectedOrganisationId = signal<string | undefined>(undefined);
  protected readonly totalCount = signal<number | undefined>(undefined);
  protected readonly loadError = signal(false);
  private readonly gridApi = signal<GridApi | undefined>(undefined);
  protected readonly csvDownload = createCsvDownload({
    gridApi: this.gridApi,
    fetchCsv: (sortModel, filterModel) =>
      this.organisationService.getOrganisationsCsv(sortModel, filterModel),
    fileName: 'organisations.csv',
    failedMessageKey: 'organisation.error.downloadFailed',
  });

  protected readonly gridOptions: GridOptions<OrganisationResponseDto> = {
    domLayout: 'normal',
  };

  protected readonly datasource = createPagedDatasource(
    (params) => this.organisationService.getOrganisations(params),
    this.totalCount,
    this.loadError,
  );

  constructor(view: WorkbenchView) {
    // SCION Workbench: Dynamically update the tab title whenever the data changes
    effect(() => {
      view.title = this.translateService.translate('tab.organisation')();
    });

    // the infinite row model has no way to notice a save in the dialog, so the visible pages are
    // fetched again on every one
    effect(() => {
      this.organisationService.organisationsSaved();
      untracked(() => this.gridApi()?.refreshInfiniteCache());
    });
  }

  onGridReady(api: GridApi): void {
    this.gridApi.set(api);
  }

  onSelectionChanged(rows: OrganisationResponseDto[]): void {
    this.selectedOrganisationId.set(rows[0]?.id);
  }

  onCreate(): void {
    this.formDialog.open(OrganisationEdit);
  }

  onEdit(): void {
    const organisationId = this.selectedOrganisationId();
    if (organisationId === undefined) return;

    this.formDialog.open(OrganisationEdit, { organisationId });
  }

  onSearch(term: string): void {
    // Infinite row model has no quick filter, so the search box drives the name column filter.
    const api = this.gridApi();
    if (!api) return;
    const { name: _, ...otherFilters } = api.getFilterModel();
    const trimmed = term.trim();
    api.setFilterModel(
      trimmed
        ? { ...otherFilters, name: { filterType: 'text', type: 'contains', filter: trimmed } }
        : otherFilters,
    );
  }

  protected getOrganisationRowId = (row: OrganisationResponseDto): string => String(row.id);
}
