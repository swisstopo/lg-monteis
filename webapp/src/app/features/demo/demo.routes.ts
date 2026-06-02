import { Routes } from '@angular/router';

export const DEMO_ROUTES: Routes = [
  {
    path: 'overview',
    loadComponent: () => import('./overview/overview'),
  },
  {
    path: 'metrics-menu',
    loadComponent: () => import('./metrics-menu/metrics-menu'),
  },
  {
    path: 'cds-table',
    loadComponent: () => import('./cds-table/cds-table'),
  },
  {
    path: 'ag-grid',
    loadComponent: () => import('./ag-grid-record/ag-grid-record'),
  }
];
