import { Routes } from '@angular/router';

export const EXPERIMENT_ROUTES: Routes = [
  {
    path: 'experiment-table',
    loadComponent: () => import('./experiment-table/experiment-table'),
  },
];
