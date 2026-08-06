import { Component } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { RouteButton } from '../../../ui/buttons/route-button/route-button';

@Component({
  selector: 'app-sensor-menu',
  imports: [RouteButton, TranslatePipe],
  templateUrl: './experiment-menu.html',
  styleUrl: './experiment-menu.scss',
})
export default class ExperimentMenu {}
