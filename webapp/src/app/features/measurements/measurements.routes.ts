import { Routes } from '@angular/router';

export const MEASUREMENTS_ROUTES: Routes = [
  {
    path: 'measurements-overview',
    loadComponent: () => import('./measurements-overview/measurements-overview'),
  },
  {
    path: 'measurements-menu',
    loadComponent: () => import('./measurements-menu/measurements-menu'),
  },
  {
    path: 'measurements-table',
    loadComponent: () => import('./measurements-table/measurements-table'),
  },
  {
    path: 'measurements-visualization',
    loadComponent: () => import('./measurements-visualization/measurements-visualization'),
  },
];
