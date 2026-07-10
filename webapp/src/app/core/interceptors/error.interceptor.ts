import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { normalizeErrors } from '../errors/error-normalizer';
import { Notification } from '../services/notification';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const notification = inject(Notification);

  return next(req).pipe(
    catchError((response: HttpErrorResponse) => {
      const errors = normalizeErrors(response.error);
      const is422 = response.status === 422;

      for (const error of errors) {
        // Global definition: Field is empty, or explicitly marked 'global' by your Spring advice
        const isGlobal = !error.field || error.field === 'global';

        // Display in toaster if it's NOT a form validation (422) OR if it is a global error
        if (!is422 || isGlobal) {
          notification.error(error.messageKey!, error.params);
        }
      }
      return throwError(() => errors);
    }),
  );
};
