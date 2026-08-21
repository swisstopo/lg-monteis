import { Routes } from '@angular/router';

export const MENU_ROUTES: Routes = [
  {
    path: 'setup-menu',
    loadComponent: () => import('./setup-menu/setup-menu'),
  },
];
