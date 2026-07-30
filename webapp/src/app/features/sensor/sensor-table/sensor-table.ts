import { Component, effect, inject, signal } from '@angular/core';
import { WorkbenchView } from '@scion/workbench';
import { SensorResponseDto } from '../../../core/generated';
import Table from '../../../ui/table/table';
import { SensorService } from '../services/sensor.service';
import { TableHeader } from '../table-header/table-header';
import { createColumns } from './columns';

@Component({
  selector: 'app-sensor-table',
  imports: [Table, TableHeader],
  templateUrl: './sensor-table.html',
  styleUrl: './sensor-table.scss',
})
export default class SensorTable {
  protected sensorService = inject(SensorService);

  protected wrappedCols = createColumns();
  protected selectedSensorId = signal<number | undefined>(undefined);

  constructor(view: WorkbenchView) {
    // SCION Workbench: Dynamically update the tab title whenever the data changes
    effect(() => {
      const count = this.sensorService.allSensors.value()?.length ?? 0;
      view.title = `Setup Sensors (${count})`;
    });
  }

  onWrappedRow(row: SensorResponseDto) {
    console.log(row);
  }

  onSelectionChanged(rows: SensorResponseDto[]): void {
    this.selectedSensorId.set(rows[0]?.id);
  }
}
