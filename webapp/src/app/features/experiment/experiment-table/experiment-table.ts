import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { PermissionsService } from '@core/auth/permissions.service';
import { ExperimentResponseDto } from '@core/generated';
import { ToastService } from '@core/notifications/toast.service';
import { openExperimentDialog } from '@features/experiment/experiment-dialog/experiment-dialog';
import { ExperimentService } from '@features/experiment/services/experiment.service';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { WorkbenchView } from '@scion/workbench';
import { FileDownloadService } from '@ui/file-download/file-download.service';
import { InlineError } from '@ui/inline-error/inline-error';
import { TableHeader } from '@ui/table-header/table-header';
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
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class ExperimentTable {
  private readonly datePipe = inject(DatePipe);
  private readonly dialog = inject(MatDialog);
  private readonly experimentService = inject(ExperimentService);
  private readonly translateService = inject(TranslateService);
  private readonly fileDownloadService = inject(FileDownloadService);
  private readonly toastService = inject(ToastService);
  protected readonly permissions = inject(PermissionsService);

  readonly searchTerm = signal<string>('');

  protected readonly wrappedCols = createColumns(this.datePipe);
  protected readonly selectedExperimentId = signal<string | undefined>(undefined);
  protected readonly canEditSelected = computed(() => {
    const experimentId = this.selectedExperimentId();
    return experimentId !== undefined && this.permissions.canWriteExperiment(experimentId);
  });
  protected readonly totalCount = signal<number | undefined>(undefined);
  protected readonly loadError = signal(false);
  protected readonly downloading = signal(false);
  private readonly gridApi = signal<GridApi | undefined>(undefined);

  protected readonly gridOptions: GridOptions<ExperimentResponseDto> = {
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

  protected readonly datasource = createPagedDatasource(
    (params) => this.experimentService.getExperiments(params),
    this.totalCount,
    this.loadError,
  );

  constructor(view: WorkbenchView) {
    // SCION Workbench: Dynamically update the tab title whenever the data changes
    effect(() => {
      view.title = this.translateService.translate('tab.experiment')();
    });

    // Re-fetch the currently visible pages whenever an experiment is saved elsewhere (e.g. via
    // the dialog) - the infinite row model otherwise has no way to know.
    effect(() => {
      this.experimentService.experimentsSaved();
      untracked(() => this.gridApi()?.refreshInfiniteCache());
    });
  }

  onGridReady(api: GridApi): void {
    this.gridApi.set(api);
  }

  onSelectionChanged(rows: ExperimentResponseDto[]): void {
    this.selectedExperimentId.set(rows[0]?.id);
  }

  onCreate(): void {
    openExperimentDialog(this.dialog);
  }

  onOpenSelected(): void {
    const experimentId = this.selectedExperimentId();
    if (experimentId !== undefined) {
      openExperimentDialog(this.dialog, experimentId);
    }
  }

  async onDownload(): Promise<void> {
    const api = this.gridApi();
    if (!api) return;

    this.downloading.set(true);
    try {
      const { sortModel, filterModel } = toGridFilterSortParams(api);
      const blob = await this.experimentService.getExperimentsCsv(sortModel, filterModel);
      this.fileDownloadService.download(blob, 'experiments.csv');
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
