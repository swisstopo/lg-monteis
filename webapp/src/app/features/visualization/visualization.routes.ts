import { Routes } from '@angular/router';

export const VISUALIZATION_ROUTES: Routes = [
  {
    path: 'visualization',
    loadComponent: () => import('./poc/poc'),
  },
  {
    path: 'visualization-menu',
    loadComponent: () => import('./visualization-menu/visualization-menu'),
  },
];
