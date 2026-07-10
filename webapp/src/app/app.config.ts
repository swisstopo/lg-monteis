import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';
import { APP_ROUTES } from './config/routes.config';
import { workbenchConfig } from './config/workbench.config';
import { BASE_PATH } from './core/generated';
import { errorInterceptor } from './core/interceptors/error.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZonelessChangeDetection(),
    provideBrowserGlobalErrorListeners(),
    workbenchConfig,
    provideRouter(APP_ROUTES, withComponentInputBinding()),
    provideAnimations(), // temporary: required until SCION Workbench drops the deprecated Angular animations dependency.
    provideHttpClient(withInterceptors([errorInterceptor])),
    provideTranslateService({
      loader: provideTranslateHttpLoader({
        prefix: '/i18n/',
        suffix: '.json',
      }),
    }),
    { provide: BASE_PATH, useValue: '' },
  ],
};
