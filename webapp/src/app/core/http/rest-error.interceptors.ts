import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { ErrorDto } from '@core/generated';
import { ToastService } from '@core/notifications/toast.service';
import { TranslateService } from '@ngx-translate/core';
import { OAuthService } from 'angular-oauth2-oidc';
import { catchError, throwError } from 'rxjs';
import { AppErrorResponse } from './api-error.model';
import { SKIP_GLOBAL_ERROR_TOAST } from './http-context';

/**
 * Shows a toast for every REST error with target `GLOBAL`, regardless of
 * which component/service triggered the request. `FORM`/`FIELD` errors are
 * left untouched so callers can still display them next to the relevant
 * form/field.
 *
 * Requests marked with {@link SKIP_GLOBAL_ERROR_TOAST} are passed through untouched.
 */
export const restErrorInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.context.get(SKIP_GLOBAL_ERROR_TOAST)) {
    return next(req);
  }

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
    // 401 is raised by Spring Security's filter chain, before any controller runs, so it never
    // carries a body matching our ErrorDto contract. 403/404 do when the backend's access policy
    // decides them (e.g. `access.denied`, `object.not-found`); without such a body they keep a
    // fixed fallback message.
    if (error.isUnauthorized()) {
      showUnauthorizedToaster();
    } else if (error.isForbidden()) {
      showBackendMessagesOrFallback(error, 'error.auth.forbidden');
    } else if (error.isNotFound()) {
      showBackendMessagesOrFallback(error, 'error.system.generic');
    } else {
      const globalErrors = error.dtosTargetGlobalOrUndefined();
      if (globalErrors.length > 0) {
        showGlobalErrorsToaster(globalErrors);
      } else if (error.isServerError()) {
        showGenericErrorToaster();
      }
    }
  }

  function showBackendMessagesOrFallback(error: AppErrorResponse, fallbackMessageKey: string) {
    const backendMessages = error
      .dtosTargetGlobalOrUndefined()
      .filter((err) => err.target === ErrorDto.TargetEnum.Global && !!err.messageKey);
    if (backendMessages.length > 0) {
      showGlobalErrorsToaster(backendMessages);
    } else {
      toastService.error(translateService.translate(fallbackMessageKey)());
    }
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
