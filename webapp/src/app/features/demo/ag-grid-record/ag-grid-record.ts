import {Component, inject} from '@angular/core';
import {DatePipe} from '@angular/common';
import Table from '../../../ui/table/table';
import { createColumns } from './columns';
import { ReadSimpleMetric } from '../../../core/generated';
import { rxResource } from '@angular/core/rxjs-interop';
import { DemoControllerService } from '../../../core/generated';

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
    stream: () => this.demoService.getMetrics(100),
  });

  protected wrappedCols = createColumns(this.datePipe);

  onWrappedRow(row: ReadSimpleMetric) {
    console.log(row);
  }
}
