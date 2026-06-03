import {Component, CUSTOM_ELEMENTS_SCHEMA, effect, inject, input} from '@angular/core';
import {WorkbenchView} from '@scion/workbench';
import {DatePipe} from '@angular/common';
import { DemoControllerService } from '../../../core/generated/api/demoController.service';
import '@carbon/web-components/es/components/data-table/index.js';
import { rxResource } from '@angular/core/rxjs-interop';
@Component({
  selector: 'app-timestamp-record',
  imports: [DatePipe],
  templateUrl: './cds-table.html',
  styleUrl: './cds-table.scss',
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export default class CdsTable {
  protected demoService = inject(DemoControllerService);
  protected metricsResource = rxResource({
    stream: () => this.demoService.getMetrics(100),
  });

  constructor(view: WorkbenchView) {
    // SCION Workbench: Dynamically update the tab title whenever the data changes
    effect(() => {
      const count = this.metricsResource.value()?.length ?? 0;
      view.title = `CDS-Table (${count})`;
    });
  }
}
