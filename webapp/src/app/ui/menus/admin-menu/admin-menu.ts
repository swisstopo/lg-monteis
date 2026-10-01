import { Component } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { RouteButton } from '@ui/buttons/route-button/route-button';

@Component({
  selector: 'app-admin-menu',
  imports: [RouteButton, TranslatePipe],
  templateUrl: './admin-menu.html',
  styleUrl: './admin-menu.scss',
})
export default class AdminMenu {}
