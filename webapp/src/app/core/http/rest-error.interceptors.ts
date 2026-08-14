import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { OAuthService } from 'angular-oauth2-oidc';
import { catchError, throwError } from 'rxjs';
import { ErrorDto } from '../generated';
import { ToastService } from '../notifications/toast.service';
import { toErrorDtos } from './api-error.model';

/**
 * Shows a toast for every REST error with target `GLOBAL`, regardless of
 * which component/service triggered the request. `FORM`/`FIELD` errors are
 * left untouched so callers can still display them next to the relevant
 * form/field.
 */
export const restErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const toastService = inject(ToastService);
  const translateService = inject(TranslateService);
  const oauthService = inject(OAuthService);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse) {
        // 401/403 are raised by Spring Security's filter chain, before any controller runs, so
        // they never carry a body matching our ErrorDto contract
        if (error.status === 401) {
          toastService.error(translateService.translate('error.auth.sessionExpired')(), undefined, {
            label: translateService.translate('error.auth.logInAgain')(),
            onClick: () => oauthService.initLoginFlow(), // re-login
          });
        } else if (error.status === 403) {
          toastService.error(translateService.translate('error.auth.forbidden')());
        } else {
          const globalErrors = toErrorDtos(error).filter(
            (err) => err.target === ErrorDto.TargetEnum.Global || err.target === undefined,
          );

          if (globalErrors.length > 0) {
            globalErrors.forEach((err) =>
              toastService.error(
                translateService.translate(err.messageKey ?? 'error.system.internal')(),
              ),
            );
          } else if (error.status === 0 || error.status >= 500) {
            // Network failures or backend crashes that never reach our ErrorDto
            // contract (e.g. proxy/HTML error pages) still need to surface to the user.
            toastService.error(translateService.translate('error.system.generic')());
          }
        }
      }
      return throwError(() => error);
    }),
  );
};
