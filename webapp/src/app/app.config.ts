import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { APP_ROUTES } from './config/routes.config';
import { workbenchConfig } from './config/workbench.config';
import { authInterceptor } from './core/auth/auth.interceptor';
import { provideAuth } from './core/auth/provide-auth';
import { provideAppDateConfig } from './core/date/date.provider';
import { BASE_PATH } from './core/generated';
import { restErrorInterceptor } from './shared/interceptors/rest-error.interceptors';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZonelessChangeDetection(),
    provideBrowserGlobalErrorListeners(),
    workbenchConfig,
    provideTranslateService(),
    provideRouter(APP_ROUTES, withComponentInputBinding()),
    provideAnimations(), // temporary: required until SCION Workbench drops the deprecated Angular animations dependency.
    provideHttpClient(withInterceptors([authInterceptor, restErrorInterceptor])),
    provideAuth(),
    provideAppDateConfig(),
    { provide: BASE_PATH, useValue: '' },
  ],
};
