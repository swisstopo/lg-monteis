import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection
} from '@angular/core';
import {provideRouter, withComponentInputBinding} from '@angular/router';
import {provideAnimations} from '@angular/platform-browser/animations';
import { workbenchConfig } from './config/workbench.config';
import { APP_ROUTES } from './config/routes.config';
import { provideHttpClient } from '@angular/common/http';
import {BASE_PATH} from './core/generated';

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
