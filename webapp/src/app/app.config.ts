import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection
} from '@angular/core';
import {MAIN_AREA, provideWorkbench, WorkbenchIconDescriptor, WorkbenchLayoutFactory} from '@scion/workbench';
import {provideRouter, withComponentInputBinding} from '@angular/router';
import {provideAnimations} from '@angular/platform-browser/animations';
import {Icon} from './shared/icon/icon';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZonelessChangeDetection(),
    provideBrowserGlobalErrorListeners(),
    provideWorkbench({
      iconProvider: (icon: string): WorkbenchIconDescriptor | undefined => {
        if (icon.startsWith('workbench.')) {
          return undefined; // return `undefined` to not replace built-in workbench icons
        }
        return {
          component: Icon,
          inputs: {icon, size:"16"},
        };
      },
      layout: (factory: WorkbenchLayoutFactory) => factory
        .addPart(MAIN_AREA)
        .navigatePart(MAIN_AREA, ['overview'])
        .addPart('timeseries', {dockTo: 'left-top'}, {label:'Time Series', icon: 'business-metrics'})
        .navigatePart('timeseries', ['timeseries'])
        .activatePart('timeseries'),
    }),
    provideRouter([
      {path: 'overview', loadComponent: () => import('./features/demo/overview/overview')},
      {path: 'timeseries', loadComponent: () => import('./features/demo/metrics-menu/metrics-menu')},
      {path: 'timeseriestable', loadComponent: () => import('./features/demo/cds-table/cds-table')},
      {path: 'ag-grid', loadComponent: () => import('./features/demo/ag-grid-record/ag-grid-record')},
    ], withComponentInputBinding()),
    provideAnimations(), // temporary: required until SCION Workbench drops the deprecated Angular animations dependency.
  ],
};
