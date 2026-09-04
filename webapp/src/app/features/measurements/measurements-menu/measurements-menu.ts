import { Component } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { RouteButton } from '../../../ui/buttons/route-button/route-button';

@Component({
  selector: 'app-measurements-menu',
  imports: [RouteButton, TranslatePipe],
  templateUrl: './measurements-menu.html',
  styleUrl: './measurements-menu.scss',
})
export default class MeasurementsMenu {}
