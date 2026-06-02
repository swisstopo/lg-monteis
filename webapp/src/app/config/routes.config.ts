import { Routes } from '@angular/router';
import { DEMO_ROUTES } from '../features/demo/demo.routes';

export const APP_ROUTES: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'overview',
  },
  {
    path: '',
    children: DEMO_ROUTES,
  }
]
