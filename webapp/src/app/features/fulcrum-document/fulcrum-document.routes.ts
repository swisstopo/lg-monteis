import { Routes } from '@angular/router';

export const FULCRUM_DOCUMENT_ROUTES: Routes = [
  {
    path: 'fulcrum-document-table',
    loadComponent: () => import('./fulcrum-document-table/fulcrum-document-table'),
  },
];
