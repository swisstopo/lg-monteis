import { Routes } from '@angular/router';
import { DEMO_ROUTES } from '../features/demo/demo.routes';
import { SideNav } from '../layout/side-nav/side-nav';

export const APP_ROUTES: Routes = [
  {
    path: '',
    component: SideNav,
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'overview',
      },
      ...DEMO_ROUTES,
    ],
  },
];
