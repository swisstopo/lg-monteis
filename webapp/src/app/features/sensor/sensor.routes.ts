import { Routes } from '@angular/router';
import { adminGuard } from '@core/auth/admin.guard';

export const SENSOR_ROUTES: Routes = [
  {
    path: 'sensor-table',
    // Sensor management is admin-only (the backend enforces it; this only keeps others out of the view).
    canActivate: [adminGuard],
    loadComponent: () => import('./sensor-table/sensor-table'),
  },
];
