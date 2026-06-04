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
    path: 'ag-mat-table',
    loadComponent: () => import('./angular-material-table/ag-mat-table'),
  },
  {
    path: 'ag-grid',
    loadComponent: () => import('./ag-grid-record/ag-grid-record'),
  },
  {
    path: 'dialog',
    loadComponent: () => import('./dialog/dialog'),
  }
];
