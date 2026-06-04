import {Component, effect, inject} from '@angular/core';
import {WorkbenchView} from '@scion/workbench';
import {DatePipe} from '@angular/common';
import { DemoControllerService } from '../../../core/generated';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatTableModule} from '@angular/material/table';
@Component({
  selector: 'app-ag-mat-table',
  imports: [DatePipe, MatTableModule],
  templateUrl: './ag-mat-table.html',
  styleUrl: './ag-mat-table.scss',
})
export default class AgMatTable {
  protected demoService = inject(DemoControllerService);
  protected metricsResource = rxResource({
    stream: () => this.demoService.getMetrics(100),
  });
  protected displayedColumns = ['timestamp', 'val'];
  constructor(view: WorkbenchView) {
    // SCION Workbench: Dynamically update the tab title whenever the data changes
    effect(() => {
      const count = this.metricsResource.value()?.length ?? 0;
      view.title = `AG-Material-Table (${count})`;
    });
  }
}
