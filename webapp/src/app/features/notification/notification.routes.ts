import { Routes } from '@angular/router';
import { authorizationGuard, writeExperiment } from '@core/auth/authorization.guard';

export const NOTIFICATION_ROUTES: Routes = [
  {
    path: 'monitoring-menu',
    loadComponent: () => import('./monitoring-menu/monitoring-menu'),
  },
  // monitoring/overview (MON-247) and monitoring/closely-monitored (MON-246) go before :experimentId
  {
    path: 'monitoring/:experimentId',
    canActivate: [authorizationGuard(writeExperiment('experimentId'))],
    loadComponent: () => import('./notification-page/notification-page'),
  },
];
