import {Component, CUSTOM_ELEMENTS_SCHEMA} from '@angular/core';
import { RouteButton } from '../../../ui/buttons/route-button/route-button';
@Component({
  selector: 'app-metrics-menu',
  imports: [RouteButton],
  templateUrl: './metrics-menu.html',
  styleUrl: './metrics-menu.scss',
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export default class MetricsMenu {
}
