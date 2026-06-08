import { provideHttpClient } from '@angular/common/http';
import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { APP_ROUTES } from './config/routes.config';
import { workbenchConfig } from './config/workbench.config';
import { BASE_PATH } from './core/generated';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZonelessChangeDetection(),
    provideBrowserGlobalErrorListeners(),
    workbenchConfig,
    provideRouter(APP_ROUTES, withComponentInputBinding()),
    provideAnimations(), // temporary: required until SCION Workbench drops the deprecated Angular animations dependency.
    provideHttpClient(),
    { provide: BASE_PATH, useValue: '' },
  ],
};
