import {Component, computed, CUSTOM_ELEMENTS_SCHEMA, effect, inject, input} from '@angular/core';
import {WorkbenchView} from '@scion/workbench';
import {DatePipe} from '@angular/common';
import Demo from '../services/demo';
import '@carbon/web-components/es/components/data-table/index.js';
@Component({
  selector: 'app-timestamp-record',
  imports: [
    DatePipe,
  ],
  templateUrl: './cds-table.html',
  styleUrl: './cds-table.scss',
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export default class CdsTable {

  protected demoService = inject(Demo);

  constructor(view: WorkbenchView) {
    // SCION Workbench: Dynamically update the tab title whenever the data changes
    effect(() => {
      const count = this.demoService.metricsResource.value()?.length ?? 0;
      view.title = `CDS-Table (${count})`;
    });
  }
}
