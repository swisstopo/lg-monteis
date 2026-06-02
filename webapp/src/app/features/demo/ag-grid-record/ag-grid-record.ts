import {Component, CUSTOM_ELEMENTS_SCHEMA, effect, inject} from '@angular/core';
import Demo from '../services/demo';
import {WorkbenchView} from '@scion/workbench';
import {DatePipe} from '@angular/common';
import '@carbon/web-components/es/components/loading/index.js';
import Table from '../../../ui/table/table';
import { createColumns } from './columns';
import { ReadSimpleMetric } from '../../../core/models/read-simple-metric';
// TODO: setup AG Grid properly
@Component({
  selector: 'app-ag-grid-record',
  imports: [Table],
  providers: [DatePipe],
  templateUrl: './ag-grid-record.html',
  styleUrl: './ag-grid-record.scss',
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export default class AgGridRecord {
  protected demoService = inject(Demo);
  private datePipe = inject(DatePipe);

  protected wrappedCols = createColumns(this.datePipe);
  constructor(view: WorkbenchView) {
    // SCION Workbench: Dynamically update the tab title whenever the data changes
    effect(() => {
      const count = this.demoService.metricsResource.value()?.length ?? 0;
      view.title = `AG-Grid (${count})`;
    });
  }

  onWrappedRow(row: ReadSimpleMetric) {
    console.log(row);
  }
}
