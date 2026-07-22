import { DatePipe } from '@angular/common';
import { Component, effect, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { WorkbenchView } from '@scion/workbench';
import { OverviewControllerService, ReadSimpleMetricDto } from '../../../core/generated';
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
  private readonly datePipe = inject(DatePipe);

  protected overviewService = inject(OverviewControllerService);
  protected metricsResource = rxResource({
    stream: () => this.overviewService.getMetrics(50),
  });

  protected wrappedCols = createColumns(this.datePipe);
  constructor(view: WorkbenchView) {
    // SCION Workbench: Dynamically update the tab title whenever the data changes
    effect(() => {
      const count = this.metricsResource.value()?.length ?? 0;
      view.title = `AG-Grid (${count})`;
    });
    const Bestway = 'COMMENT';
  }

  onWrappedRow(row: ReadSimpleMetricDto) {
    console.log(row);
  }
}
