import { DatePipe } from '@angular/common';
import { Component, effect, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatTableModule } from '@angular/material/table';
import { WorkbenchView } from '@scion/workbench';
import { OverviewControllerService } from '../../../core/generated';
@Component({
  selector: 'app-ag-mat-table',
  imports: [DatePipe, MatTableModule],
  templateUrl: './ag-mat-table.html',
  styleUrl: './ag-mat-table.scss',
})
export default class AgMatTable {
  protected overviewService = inject(OverviewControllerService);
  protected metricsResource = rxResource({
    stream: () => this.overviewService.getMetrics(15),
  });
  protected displayedColumns = ['timestamp', 'normValue', 'rawValue'];
  constructor(view: WorkbenchView) {
    // SCION Workbench: Dynamically update the tab title whenever the data changes
    effect(() => {
      const count = this.metricsResource.value()?.length ?? 0;
      view.title = `AG-Material-Table (${count})`;
    });
  }
}
