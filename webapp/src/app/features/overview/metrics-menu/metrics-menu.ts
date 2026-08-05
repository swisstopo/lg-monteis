import { Component } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { RouteButton } from '../../../ui/buttons/route-button/route-button';
@Component({
  selector: 'app-metrics-menu',
  imports: [RouteButton, TranslatePipe],
  templateUrl: './metrics-menu.html',
  styleUrl: './metrics-menu.scss',
})
export default class MetricsMenu {}
