import { Component, inject, resource } from '@angular/core';
import { ExperimentService } from '@features/experiment/services/experiment.service';
import { TranslatePipe } from '@ngx-translate/core';
import { RouteButton } from '@ui/buttons/route-button/route-button';

@Component({
  selector: 'app-monitoring-menu',
  imports: [RouteButton, TranslatePipe],
  templateUrl: './monitoring-menu.html',
  styleUrl: './monitoring-menu.scss',
})
export default class MonitoringMenu {
  private readonly experimentService = inject(ExperimentService);

  protected readonly writableExperiments = resource({
    loader: () => this.experimentService.findWritableExperiments(),
  });
}
