import { Routes } from '@angular/router';
import { adminGuard } from '@core/auth/admin.guard';

export const MENU_ROUTES: Routes = [
  {
    path: 'setup-menu',
    loadComponent: () => import('./setup-menu/setup-menu'),
  },
  {
    path: 'admin-menu',
    canActivate: [adminGuard],
    loadComponent: () => import('./admin-menu/admin-menu'),
  },
];
