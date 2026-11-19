import { Routes } from '@angular/router';
import { authorizationGuard, WRITE_EXPERIMENT } from '@core/auth/authorization.guard';

export const NOTIFICATION_ROUTES: Routes = [
  {
    path: 'monitoring-menu',
    loadComponent: () => import('./monitoring-menu/monitoring-menu'),
  },
  // monitoring/overview (MON-247) and monitoring/closely-monitored (MON-246) go before :experimentId
  {
    path: 'monitoring/:experimentId',
    canActivate: [authorizationGuard(WRITE_EXPERIMENT)],
    loadComponent: () => import('./notification-page/notification-page'),
  },
];
