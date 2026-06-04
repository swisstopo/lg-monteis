import {Component, inject} from '@angular/core';
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
}
