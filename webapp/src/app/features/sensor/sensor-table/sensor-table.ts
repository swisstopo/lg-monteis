import { DatePipe } from '@angular/common';
import { Component, effect, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { WorkbenchView } from '@scion/workbench';
import { OverviewControllerService, ReadSimpleMetricDto } from '../../../core/generated';
import Table from '../../../ui/table/table';
import { TableHeader } from '../table-header/table-header';
import { createColumns } from './columns';

@Component({
  selector: 'app-sensor-table',
  imports: [Table, TableHeader],
  providers: [DatePipe],
  templateUrl: './sensor-table.html',
  styleUrl: './sensor-table.scss',
})
export default class SensorTable {
  private readonly datePipe = inject(DatePipe);

  protected overviewService = inject(OverviewControllerService);
  protected metricsResource = rxResource({
    stream: () => this.overviewService.getMetrics(50),
  });

  protected wrappedCols = createColumns(this.datePipe);
  protected selectedSensorId = signal<number | undefined>(undefined);

  constructor(view: WorkbenchView) {
    // SCION Workbench: Dynamically update the tab title whenever the data changes
    effect(() => {
      const count = this.metricsResource.value()?.length ?? 0;
      view.title = `Setup Sensors`;
    });
  }

  onWrappedRow(row: ReadSimpleMetricDto) {
    console.log(row);
  }

  onSelectionChanged(row: ReadSimpleMetricDto | undefined): void {
    this.selectedSensorId.set(row?.sensorId !== undefined ? Number(row.sensorId) : undefined);
  }
}
