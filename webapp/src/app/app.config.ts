import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection
} from '@angular/core';
import {provideRouter, withComponentInputBinding} from '@angular/router';
import { APP_ROUTES } from './config/routes.config';
import { provideHttpClient } from '@angular/common/http';
import {BASE_PATH} from './core/generated';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZonelessChangeDetection(),
    provideBrowserGlobalErrorListeners(),
    provideRouter(APP_ROUTES, withComponentInputBinding()),
    provideHttpClient(),
    { provide: BASE_PATH, useValue: '' },
  ],
};
