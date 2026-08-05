import { Routes } from '@angular/router';

export const OVERVIEW_ROUTES: Routes = [
  {
    path: 'overview',
    loadComponent: () => import('./overview/overview'),
  },
  {
    path: 'metrics-menu',
    loadComponent: () => import('./metrics-menu/metrics-menu'),
  },
  {
    path: 'measurements',
    loadComponent: () => import('./overview-table/overview-table'),
  },
];
