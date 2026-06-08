import { Component, input } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { WorkbenchRouterLinkDirective } from '@scion/workbench';

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
