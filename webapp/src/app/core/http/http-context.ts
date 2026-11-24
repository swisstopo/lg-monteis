import { HttpContext, HttpContextToken, HttpStatusCode } from '@angular/common/http';

/**
 * Set on a request to keep `restErrorInterceptor` from showing a global toast for it, for callers
 * that render errors themselves. Useful for requests that come in bulk, where one toast per failure
 * would bury the UI.
 */
export const SKIP_GLOBAL_ERROR_TOAST = new HttpContextToken(() => false);

export const SKIP_GLOBAL_ERROR_TOAST_FOR = new HttpContextToken<readonly HttpStatusCode[]>(
  () => [],
);

/** Without statuses no error of the request toasts, with statuses only those stay silent. */
export function skipGlobalErrorToast(...statuses: HttpStatusCode[]): HttpContext {
  return statuses.length === 0
    ? new HttpContext().set(SKIP_GLOBAL_ERROR_TOAST, true)
    : new HttpContext().set(SKIP_GLOBAL_ERROR_TOAST_FOR, statuses);
}
