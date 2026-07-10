import { Routes } from '@angular/router';

export const SENSOR_ROUTES: Routes = [
  {
    path: 'sensor',
    loadComponent: () => import('./sensor-create/sensor-create'),
  },
  {
    path: 'sensor-menu',
    loadComponent: () => import('./sensor-menu/sensor-menu'),
  },
];
