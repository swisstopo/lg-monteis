import {Component, CUSTOM_ELEMENTS_SCHEMA} from '@angular/core';
import {WorkbenchRouterLinkDirective} from '@scion/workbench';
import '@carbon/web-components/es/components/button/index.js';
import '@carbon/web-components/es/components/icon/index.js';
import ChartLine16 from '@carbon/icons/es/chart--line/16';
@Component({
  selector: 'app-timeseries',
  imports: [
    WorkbenchRouterLinkDirective
  ],
  templateUrl: './metrics-menu.html',
  styleUrl: './metrics-menu.scss',
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export default class MetricsMenu {
  protected chartIcon = ChartLine16;
}
