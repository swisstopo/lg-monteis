import { Routes } from '@angular/router';

export const SENSOR_ROUTES: Routes = [
  {
    path: 'sensor-menu',
    loadComponent: () => import('./sensor-menu/sensor-menu'),
  },
  {
    path: 'sensor-table',
    loadComponent: () => import('./sensor-table/sensor-table'),
  },
];
