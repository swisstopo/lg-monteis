import { Routes } from '@angular/router';
import { ADMIN, authorizationGuard } from '@core/auth/authorization.guard';

export const ADMIN_ROUTES: Routes = [
  {
    path: 'organisation-table',
    canActivate: [authorizationGuard(ADMIN)],
    loadComponent: () => import('./organisation-table/organisation-table'),
  },
];
