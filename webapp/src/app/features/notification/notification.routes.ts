import { Routes } from '@angular/router';
import { authorizationGuard, WRITE_EXPERIMENT } from '@core/auth/authorization.guard';

export const NOTIFICATION_ROUTES: Routes = [
  {
    path: 'monitoring/:experimentId',
    canActivate: [authorizationGuard(WRITE_EXPERIMENT)],
    loadComponent: () => import('./notification-page/notification-page'),
  },
];
