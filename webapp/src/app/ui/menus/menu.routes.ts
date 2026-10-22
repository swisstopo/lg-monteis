import { Routes } from '@angular/router';
import { ADMIN, authorizationGuard } from '@core/auth/authorization.guard';

export const MENU_ROUTES: Routes = [
  {
    path: 'setup-menu',
    loadComponent: () => import('./setup-menu/setup-menu'),
  },
  {
    path: 'knowledge-menu',
    loadComponent: () => import('./knowledge-menu/knowledge-menu'),
  },
  {
    path: 'admin-menu',
    canActivate: [authorizationGuard(ADMIN)],
    loadComponent: () => import('./admin-menu/admin-menu'),
  },
];
