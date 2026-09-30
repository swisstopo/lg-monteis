import { Routes } from '@angular/router';

export const SENSOR_ROUTES: Routes = [
  {
    path: 'sensor-table',
    loadComponent: () => import('./sensor-table/sensor-table'),
  },
];
