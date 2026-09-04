import { Component, effect, inject, inputBinding, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { WorkbenchView } from '@scion/workbench';
import { GridApi } from 'ag-grid-community';
import { SensorResponseDto } from '../../../core/generated';
import { InlineError } from '../../../ui/inline-error/inline-error';
import { TableHeader } from '../../../ui/table-header/table-header';
import { createPagedDatasource } from '../../../ui/table/paged-datasource.factory';
import Table from '../../../ui/table/table';
import SensorEdit from '../sensor-edit/sensor-edit';
import { SensorService } from '../services/sensor.service';
import { createColumns } from './columns';

@Component({
  selector: 'app-sensor-table',
  imports: [Table, TableHeader, TranslatePipe, MatIcon, MatButton, InlineError],
  templateUrl: './sensor-table.html',
  styleUrl: './sensor-table.scss',
})
export default class SensorTable {
  private readonly dialog = inject(MatDialog);
  protected sensorService = inject(SensorService);
  private readonly translateService = inject(TranslateService);

  readonly searchTerm = signal<string>('');

  protected wrappedCols = createColumns();
  protected selectedSensorId = signal<string | undefined>(undefined);
  protected totalCount = signal<number | undefined>(undefined);
  protected loadError = signal(false);
  private readonly gridApi = signal<GridApi | undefined>(undefined);

  protected datasource = createPagedDatasource(
    (params) => this.sensorService.getSensors(params),
    this.totalCount,
    this.loadError,
  );

  constructor(view: WorkbenchView) {
    // SCION Workbench: Dynamically update the tab title whenever the data changes
    effect(() => {
      const count = this.totalCount() ?? 0;
      view.title = this.translateService.translate('tab.sensor', { count })();
    });

    // Re-fetch the currently visible pages whenever a sensor is created/updated elsewhere
    // (e.g. via the edit dialog) - the infinite row model otherwise has no way to know.
    effect(() => {
      if (this.sensorService.sensorsChanged()) {
        this.gridApi()?.refreshInfiniteCache();
        this.sensorService.sensorsChanged.set(false);
      }
    });
  }

  onGridReady(api: GridApi): void {
    this.gridApi.set(api);
  }

  onWrappedRow(row: SensorResponseDto) {
    console.log(row);
  }

  onSelectionChanged(rows: SensorResponseDto[]): void {
    this.selectedSensorId.set(rows[0]?.id);
  }

  onCreate(): void {
    this.dialog.open(SensorEdit, { width: '60vw', maxWidth: '1200px', autoFocus: true });
  }

  onEdit(): void {
    const sensorId = this.selectedSensorId();
    if (sensorId === undefined) return;

    this.dialog.open(SensorEdit, {
      width: '60vw',
      maxWidth: '1200px',
      autoFocus: true,
      bindings: [inputBinding('sensorId', () => sensorId)],
    });
  }

  onDownload(): void {
    // Not implemented yet.
  }

  onSearch(term: string): void {
    this.searchTerm.set(term);
  }

  protected getSensorRowId = (row: SensorResponseDto): string => String(row.id);
}
