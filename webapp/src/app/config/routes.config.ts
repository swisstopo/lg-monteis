import { Routes } from '@angular/router';
import { authGuard } from '../core/auth/auth.guard';
import { OVERVIEW_ROUTES } from '../features/overview/overview.routes';

export const APP_ROUTES: Routes = [
  {
    path: '',
    canActivateChild: [authGuard],
    children: [
      // Spread both arrays into the main application routing table
      ...OVERVIEW_ROUTES,
      // Fallback route if needed
      { path: '', redirectTo: 'overview', pathMatch: 'full' },
    ],
  },
];
