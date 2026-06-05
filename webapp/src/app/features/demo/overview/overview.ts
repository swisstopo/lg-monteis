import { Component, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatCard, MatCardContent, MatCardHeader, MatCardSubtitle } from '@angular/material/card';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { DemoControllerService } from '../../../core/generated';

@Component({
  selector: 'app-overview',
  imports: [MatCard, MatCardHeader, MatCardSubtitle, MatCardContent, MatProgressSpinner],
  templateUrl: './overview.html',
  styleUrl: './overview.scss',
})
export default class Overview {
  protected demoService = inject(DemoControllerService);

  protected metricsResource = rxResource({
    stream: () => this.demoService.getMetrics(100),
  });
}
