import { Routes } from '@angular/router';

export const SENSOR_ROUTES: Routes = [
  {
    path: 'sensor-menu',
    loadComponent: () => import('./sensor/sensor-menu/sensor-menu'),
  },
  {
    path: 'sensor-table',
    loadComponent: () => import('./sensor/sensor-table/sensor-table'),
  },
  {
    path: 'experiment-menu',
    loadComponent: () => import('./experiment/experiment-menu/experiment-menu'),
  },
  {
    path: 'experiment-table',
    loadComponent: () => import('./experiment/experiment-table/experiment-table'),
  },
];
