import { Routes } from '@angular/router';
import { authGuard } from '../core/auth/auth.guard';
import { EXPERIMENT_ROUTES } from '../features/experiment/experiment.routes';
import { MEASUREMENTS_ROUTES } from '../features/measurements/measurements.routes';
import { SENSOR_ROUTES } from '../features/sensor/sensor.routes';
import { MENU_ROUTES } from '../ui/menus/menu.routes';

export const APP_ROUTES: Routes = [
  {
    path: '',
    canActivateChild: [authGuard],
    children: [
      // Spread both arrays into the main application routing table
      ...MEASUREMENTS_ROUTES,
      ...SENSOR_ROUTES,
      ...EXPERIMENT_ROUTES,
      ...MENU_ROUTES,
      // Fallback route if needed
      { path: '', redirectTo: 'measurements-overview', pathMatch: 'full' },
    ],
  },
];
