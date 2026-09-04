import { Component, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatCard, MatCardContent, MatCardHeader, MatCardSubtitle } from '@angular/material/card';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { TranslatePipe } from '@ngx-translate/core';
import { OverviewControllerService } from '../../../core/generated';

@Component({
  selector: 'app-measurements-overview',
  imports: [
    MatCard,
    MatCardHeader,
    MatCardSubtitle,
    MatCardContent,
    MatProgressSpinner,
    TranslatePipe,
  ],
  templateUrl: './measurements-overview.html',
  styleUrl: './measurements-overview.scss',
})
export default class MeasurementsOverview {
  protected overviewService = inject(OverviewControllerService);

  protected metricsResource = rxResource({
    stream: () => this.overviewService.getMetrics(100),
  });
}
