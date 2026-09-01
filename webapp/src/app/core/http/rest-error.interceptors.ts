import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { OAuthService } from 'angular-oauth2-oidc';
import { catchError, throwError } from 'rxjs';
import { ErrorDto } from '../generated';
import { ToastService } from '../notifications/toast.service';
import { AppErrorResponse } from './api-error.model';

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
    catchError((httpErrorResponse: unknown) => {
      if (httpErrorResponse instanceof HttpErrorResponse) {
        const appErrorResponse = new AppErrorResponse(httpErrorResponse);
        processHttpErrorResponse(appErrorResponse);
      }
      return throwError(() => httpErrorResponse);
    }),
  );

  function processHttpErrorResponse(error: AppErrorResponse) {
    // 401/403 are raised by Spring Security's filter chain, before any controller runs, so
    // they never carry a body matching our ErrorDto contract
    if (error.isUnauthorized()) {
      showUnauthorizedToaster();
    } else if (error.isForbidden()) {
      showErrorForbiddenToaster();
    } else {
      const globalErrors = error.dtosTargetGlobalOrUndefined();
      if (globalErrors.length > 0) {
        showGlobalErrorsToaster(globalErrors);
      } else if (error.isServerError()) {
        showGenericErrorToaster();
      }
    }
  }

  function showErrorForbiddenToaster() {
    toastService.error(translateService.translate('error.auth.forbidden')());
  }

  function showGlobalErrorsToaster(globalErrors: ErrorDto[]) {
    globalErrors.forEach((err) =>
      toastService.error(
        translateService.translate(err.messageKey ?? 'error.system.internal', err.params)(),
      ),
    );
  }

  function showGenericErrorToaster() {
    toastService.error(translateService.translate('error.system.generic')());
  }

  function showUnauthorizedToaster() {
    toastService.error(translateService.translate('error.auth.sessionExpired')(), undefined, {
      label: translateService.translate('error.auth.logInAgain')(),
      onClick: () => oauthService.initLoginFlow(), // re-login
    });
  }
};
