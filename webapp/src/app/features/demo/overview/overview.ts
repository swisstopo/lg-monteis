import { Component, CUSTOM_ELEMENTS_SCHEMA, inject } from '@angular/core';
import '@carbon/web-components/es/components/tile/index.js';
import '@carbon/web-components/es/components/loading/index.js';
import { DemoControllerService } from '../../../core/generated/api/demoController.service';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';

@Component({
  selector: 'app-overview',
  imports: [],
  templateUrl: './overview.html',
  styleUrl: './overview.scss',
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export default class Overview {
  protected demoService = inject(DemoControllerService);

  protected metricsResource = rxResource({
    stream: () => this.demoService.getMetrics(100),
  });
}
