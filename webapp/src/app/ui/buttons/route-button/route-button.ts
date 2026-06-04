import { Component, input } from '@angular/core';
import { WorkbenchRouterLinkDirective } from '@scion/workbench';
import {MatButton} from '@angular/material/button';
import {MatIcon} from '@angular/material/icon';

@Component({
  selector: 'app-route-button',
  imports: [WorkbenchRouterLinkDirective, MatButton, MatIcon],
  templateUrl: './route-button.html',
})
export class RouteButton {
  route = input.required<string[]>();
  icon = input<string | null>(null);
  label = input.required<string>();
}
