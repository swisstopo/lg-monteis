import { DatePipe } from '@angular/common';
import { Component, effect, inject, inputBinding, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { ExperimentResponseDto } from '@core/generated';
import { ToastService } from '@core/notifications/toast.service';
import ExperimentEdit from '@features/experiment/experiment-edit/experiment-edit';
import { ExperimentService } from '@features/experiment/services/experiment.service';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { WorkbenchView } from '@scion/workbench';
import { InlineError } from '@ui/inline-error/inline-error';
import { TableHeader } from '@ui/table-header/table-header';
import { CsvDownloadService } from '@ui/table/csv-download.service';
import { createPagedDatasource } from '@ui/table/paged-datasource.factory';
import { toGridFilterSortParams } from '@ui/table/paged-request.mapper';
import Table from '@ui/table/table';
import { GridApi, GridOptions } from 'ag-grid-community';
import { createColumns } from './columns';

@Component({
  selector: 'app-experiment-table',
  imports: [TableHeader, MatButton, MatIcon, MatProgressSpinner, TranslatePipe, Table, InlineError],
  providers: [DatePipe],
  templateUrl: './experiment-table.html',
  styleUrl: './experiment-table.scss',
})
export default class ExperimentTable {
  private readonly datePipe = inject(DatePipe);
  private readonly dialog = inject(MatDialog);
  protected experimentService = inject(ExperimentService);
  private readonly translateService = inject(TranslateService);
  private readonly csvDownloadService = inject(CsvDownloadService);
  private readonly toastService = inject(ToastService);

  readonly searchTerm = signal<string>('');

  protected wrappedCols = createColumns(this.datePipe);
  protected selectedExperimentId = signal<string | undefined>(undefined);
  protected totalCount = signal<number | undefined>(undefined);
  protected loadError = signal(false);
  protected downloading = signal(false);
  private readonly gridApi = signal<GridApi | undefined>(undefined);

  protected gridOptions: GridOptions<ExperimentResponseDto> = {
    domLayout: 'normal',
    autoSizeStrategy: {
      type: 'fitCellContents',
      scaleUpToFitGridWidth: true,
      // Without this, the scale-up only runs once on first render, so toggling the sidenav
      // (which resizes the grid's container) leaves the columns sized for the old width.
      // This is needed because the experiment has too little columns once they get more we can transform this
      // to the same logic as the other tables in this application.
      continuous: true,
    },
  };

  protected datasource = createPagedDatasource(
    (params) => this.experimentService.getExperiments(params),
    this.totalCount,
    this.loadError,
  );

  constructor(view: WorkbenchView) {
    // SCION Workbench: Dynamically update the tab title whenever the data changes
    effect(() => {
      view.title = this.translateService.translate('tab.experiment')();
    });

    // Re-fetch the currently visible pages whenever a experiment is created/updated elsewhere
    // (e.g. via the edit dialog) - the infinite row model otherwise has no way to know.
    effect(() => {
      if (this.experimentService.experimentsChanged()) {
        this.gridApi()?.refreshInfiniteCache();
        this.experimentService.experimentsChanged.set(false);
      }
    });
  }

  onGridReady(api: GridApi): void {
    this.gridApi.set(api);
  }

  onWrappedRow(row: ExperimentResponseDto) {
    console.log(row);
  }

  onSelectionChanged(rows: ExperimentResponseDto[]): void {
    this.selectedExperimentId.set(rows[0]?.id);
  }

  onCreate(): void {
    this.dialog.open(ExperimentEdit, { width: '60vw', maxWidth: '1200px', autoFocus: true });
  }

  onEdit(): void {
    const experimentId = this.selectedExperimentId();
    if (experimentId === undefined) return;

    this.dialog.open(ExperimentEdit, {
      width: '60vw',
      maxWidth: '1200px',
      autoFocus: true,
      bindings: [inputBinding('experimentId', () => experimentId)],
    });
  }

  async onDownload(): Promise<void> {
    const api = this.gridApi();
    if (!api) return;

    this.downloading.set(true);
    try {
      const { sortModel, filterModel } = toGridFilterSortParams(api);
      const blob = await this.experimentService.getExperimentsCsv(sortModel, filterModel);
      this.csvDownloadService.download(blob, 'experiments.csv');
    } catch {
      this.toastService.error(this.translateService.translate('experiment.error.downloadFailed')());
    } finally {
      this.downloading.set(false);
    }
  }

  onSearch(term: string): void {
    this.searchTerm.set(term);
  }

  protected getExperimentRowId = (row: ExperimentResponseDto): string => String(row.id);
}
