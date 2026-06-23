import { DatePipe } from '@angular/common';
import { Component, effect, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { WorkbenchView } from '@scion/workbench';
import { DemoControllerService, ReadSimpleMetric } from '../../../core/generated';
import Table from '../../../ui/table/table';
import { createColumns } from './columns';

@Component({
  selector: 'app-ag-grid-record',
  imports: [Table],
  providers: [DatePipe],
  templateUrl: './ag-grid-record.html',
  styleUrl: './ag-grid-record.scss',
})
export default class AgGridRecord {
  private datePipe = inject(DatePipe);

  protected demoService = inject(DemoControllerService);
  protected metricsResource = rxResource({
    stream: () => this.demoService.getMetrics(15),
  });

  protected wrappedCols = createColumns(this.datePipe);
  constructor(view: WorkbenchView) {
    // SCION Workbench: Dynamically update the tab title whenever the data changes
    effect(() => {
      const count = this.metricsResource.value()?.length ?? 0;
      view.title = `AG-Grid (${count})`;
    });
  }

  onWrappedRow(row: ReadSimpleMetric) {
    console.log(row);
  }
}
