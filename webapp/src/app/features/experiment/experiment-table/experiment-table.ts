import { DatePipe } from '@angular/common';
import { Component, effect, inject, inputBinding, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { WorkbenchView } from '@scion/workbench';
import { GridApi } from 'ag-grid-community';
import { ExperimentResponseDto } from '../../../core/generated';
import { TableHeader } from '../../../ui/table-header/table-header';
import { createPagedDatasource } from '../../../ui/table/paged-datasource.factory';
import Table from '../../../ui/table/table';
import ExperimentEdit from '../../experiment/experiment-edit/experiment-edit';
import { ExperimentService } from '../services/experiment.service';
import { createColumns } from './columns';

@Component({
  selector: 'app-experiment-table',
  imports: [TableHeader, MatButton, MatIcon, TranslatePipe, Table],
  providers: [DatePipe],
  templateUrl: './experiment-table.html',
  styleUrl: './experiment-table.scss',
})
export default class ExperimentTable {
  private readonly datePipe = inject(DatePipe);
  private readonly dialog = inject(MatDialog);
  protected experimentService = inject(ExperimentService);
  private readonly translateService = inject(TranslateService);

  readonly searchTerm = signal<string>('');

  protected wrappedCols = createColumns(this.datePipe);
  protected selectedExperimentId = signal<string | undefined>(undefined);
  protected totalCount = signal<number | undefined>(undefined);
  protected loadError = signal(false);
  private readonly gridApi = signal<GridApi | undefined>(undefined);

  protected datasource = createPagedDatasource(
    (params) => this.experimentService.getExperiments(params),
    this.totalCount,
    this.loadError,
  );

  constructor(view: WorkbenchView) {
    // SCION Workbench: Dynamically update the tab title whenever the data changes
    effect(() => {
      const count = this.totalCount() ?? 0;
      view.title = this.translateService.instant('tab.experiment', { count });
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

  onDownload(): void {
    // Not implemented yet.
  }

  onSearch(term: string): void {
    this.searchTerm.set(term);
  }

  protected getExperimentRowId = (row: ExperimentResponseDto): string => String(row.id);
}
