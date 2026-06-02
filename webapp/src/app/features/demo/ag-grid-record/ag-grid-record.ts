import {Component, CUSTOM_ELEMENTS_SCHEMA, effect, inject} from '@angular/core';
import Demo from '../services/demo';
import {WorkbenchView} from '@scion/workbench';
import {ColDef, GridOptions, themeBalham, themeQuartz} from 'ag-grid-community';
import {DatePipe} from '@angular/common';
import '@carbon/web-components/es/components/loading/index.js';
import {AgGridAngular} from 'ag-grid-angular';
// TODO: setup AG Grid properly
@Component({
  selector: 'app-ag-grid-record',
  imports: [
    AgGridAngular
  ],
  providers: [
    DatePipe
  ],
  templateUrl: './ag-grid-record.html',
  styleUrl: './ag-grid-record.scss',
  schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export default class AgGridRecord {
  protected demoService = inject(Demo);
  private datePipe = inject(DatePipe);

  protected theme = themeBalham;

  constructor(view: WorkbenchView) {
    // SCION Workbench: Dynamically update the tab title whenever the data changes
    effect(() => {
      const count = this.demoService.metricsResource.value()?.length ?? 0;
      view.title = `AG-Grid (${count})`;
    });
  }

  protected columnDefs: ColDef[] = [
    {
      headerName: 'Timestamp',
      field: 'timestamp',
      flex: 3,
      valueFormatter: params => this.datePipe.transform(params.value, 'medium') ?? ''
    },
    {
      headerName: 'Value',
      field: 'val',
      flex: 1
    }
  ];

  protected gridOptions: GridOptions = {
    defaultColDef: {
      sortable: true,
      filter: true,
      resizable: false
    },
    suppressCellFocus: true,
    domLayout: 'autoHeight'
  };
}
