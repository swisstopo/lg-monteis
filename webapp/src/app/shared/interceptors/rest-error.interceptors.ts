import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { catchError, throwError } from 'rxjs';
import { ErrorDto } from '../../core/generated';
import { toErrorDtos } from '../models/api-error.model';
import { ToastService } from '../services/toast.service';

/**
 * Shows a toast for every REST error with target `GLOBAL`, regardless of
 * which component/service triggered the request. `FORM`/`FIELD` errors are
 * left untouched so callers can still display them next to the relevant
 * form/field.
 */
export const restErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const toastService = inject(ToastService);
  const translateService = inject(TranslateService);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse) {
        const globalErrors = toErrorDtos(error).filter(
          (err) => err.target === ErrorDto.TargetEnum.Global || err.target === undefined,
        );

        if (globalErrors.length > 0) {
          globalErrors.forEach((err) =>
            toastService.error(translateService.instant(err.messageKey ?? 'error.system.internal')),
          );
        } else if (error.status === 0 || error.status >= 500) {
          // Network failures or backend crashes that never reach our ErrorDto
          // contract (e.g. proxy/HTML error pages) still need to surface to the user.
          toastService.error(translateService.instant('error.system.generic'));
        }
      }
      return throwError(() => error);
    }),
  );
};
