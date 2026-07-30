import { DatePipe } from '@angular/common';
import { Component, effect, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { WorkbenchView } from '@scion/workbench';
import { OverviewControllerService, ReadSimpleMetricDto } from '../../../core/generated';
import Table from '../../../ui/table/table';
import { createColumns } from './columns';

@Component({
  selector: 'app-meausrements-overview',
  imports: [Table],
  providers: [DatePipe],
  templateUrl: './overview-table.html',
  styleUrl: './overview-table.scss',
})
export default class OverviewTable {
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
      view.title = `Measurements`;
    });
  }

  onWrappedRow(row: ReadSimpleMetricDto) {
    console.log(row);
  }
}
