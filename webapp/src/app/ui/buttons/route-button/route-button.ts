import { Component, CUSTOM_ELEMENTS_SCHEMA, input } from '@angular/core';
import { WorkbenchRouterLinkDirective } from '@scion/workbench';
import '@carbon/web-components/es/components/button/index.js';
import '@carbon/web-components/es/components/icon/index.js';
import { Icon } from '../../../shared/icon/icon';
import { IconName } from '../../../shared/icon/icon.registry';

@Component({
  selector: 'app-route-button',
  imports: [WorkbenchRouterLinkDirective, Icon],
  templateUrl: './route-button.html',
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class RouteButton {
  route = input.required<string[]>();
  icon = input<IconName | null>(null);
  label = input.required<string>();
}
