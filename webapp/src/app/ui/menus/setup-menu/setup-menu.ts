import { Component } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { RouteButton } from '../../buttons/route-button/route-button';

@Component({
  selector: 'app-sensor-menu',
  imports: [RouteButton, TranslatePipe],
  templateUrl: './setup-menu.html',
  styleUrl: './setup-menu.scss',
})
export default class SetupMenu {}
