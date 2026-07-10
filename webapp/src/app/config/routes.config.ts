import { Routes } from '@angular/router';
import { DEMO_ROUTES } from '../features/demo/demo.routes';
import { SENSOR_ROUTES } from '../features/sensor/sensor.routes';

export const APP_ROUTES: Routes = [
  // Spread both arrays into the main application routing table
  ...DEMO_ROUTES,
  ...SENSOR_ROUTES,

  // Fallback route if needed
  { path: '', redirectTo: 'overview', pathMatch: 'full' },
];
