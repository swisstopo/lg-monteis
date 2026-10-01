import { Routes } from '@angular/router';
import { adminGuard } from '@core/auth/admin.guard';

export const ADMIN_ROUTES: Routes = [
  {
    path: 'organisation-table',
    canActivate: [adminGuard],
    loadComponent: () => import('./organisation-table/organisation-table'),
  },
];
