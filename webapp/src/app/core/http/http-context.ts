import { HttpContext, HttpContextToken } from '@angular/common/http';

/**
 * Set on a request to keep `restErrorInterceptor` from showing a global toast for it, for callers
 * that render errors themselves. Useful for requests that come in bulk, where one toast per failure
 * would bury the UI.
 */
export const SKIP_GLOBAL_ERROR_TOAST = new HttpContextToken(() => false);

export function skipGlobalErrorToast(): HttpContext {
  return new HttpContext().set(SKIP_GLOBAL_ERROR_TOAST, true);
}
