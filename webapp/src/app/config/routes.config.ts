import { Routes } from '@angular/router';
import { authGuard } from '../core/auth/auth.guard';
import { OVERVIEW_ROUTES } from '../features/overview/overview.routes';
import { SENSOR_ROUTES } from '../features/sensor/sensor.routes';
import { VISUALIZATION_ROUTES } from '../features/visualization/visualization.routes';

export const APP_ROUTES: Routes = [
  {
    path: '',
    canActivateChild: [authGuard],
    children: [
      // Spread both arrays into the main application routing table
      ...OVERVIEW_ROUTES,
      ...SENSOR_ROUTES,
      ...VISUALIZATION_ROUTES,

      // Fallback route if needed
      { path: '', redirectTo: 'overview', pathMatch: 'full' },
    ],
  },
];
