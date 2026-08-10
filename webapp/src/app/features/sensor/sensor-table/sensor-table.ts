import { Component, effect, inject, signal } from '@angular/core';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { WorkbenchView } from '@scion/workbench';
import { GridApi } from 'ag-grid-community';
import { SensorResponseDto } from '../../../core/generated';
import { createPagedDatasource } from '../../../ui/table/paged-datasource.factory';
import Table from '../../../ui/table/table';
import { SensorService } from '../services/sensor.service';
import { TableHeader } from '../table-header/table-header';
import { createColumns } from './columns';

@Component({
  selector: 'app-sensor-table',
  imports: [Table, TableHeader, TranslatePipe],
  templateUrl: './sensor-table.html',
  styleUrl: './sensor-table.scss',
})
export default class SensorTable {
  protected sensorService = inject(SensorService);
  private readonly i18nService = inject(TranslateService);

  protected wrappedCols = createColumns();
  protected selectedSensorId = signal<number | undefined>(undefined);
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
      view.title = this.i18nService.instant('tab.sensor', { count });
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

  protected getSensorRowId = (row: SensorResponseDto): string => String(row.id);
}
