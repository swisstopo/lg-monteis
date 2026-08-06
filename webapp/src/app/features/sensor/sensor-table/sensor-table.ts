import { Component, effect, inject, signal } from '@angular/core';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { WorkbenchView } from '@scion/workbench';
import { GridApi } from 'ag-grid-community';
import { SensorResponseDto } from '../../../core/generated';
import { createPagedDatasource } from '../../../ui/table/paged-datasource.factory';
import { TableHeader } from '../../../shared/table-header/table-header';
import Table from '../../../ui/table/table';
import SensorEdit from '../sensor-edit/sensor-edit';
import { SensorService } from '../services/sensor.service';
import { createColumns } from './columns';

@Component({
  selector: 'app-sensor-table',
  imports: [Table, TableHeader, TranslatePipe],
  templateUrl: './sensor-table.html',
  styleUrl: './sensor-table.scss',
})
export default class SensorTable {
  protected sensorService = inject(SensorService);
  protected readonly SensorEdit = SensorEdit;
  private readonly translateService = inject(TranslateService);

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

  onSearch(searchTerm: string): void {
    // Not implemented yet.
  }

  protected getSensorRowId = (row: SensorResponseDto): string => String(row.id);
}
