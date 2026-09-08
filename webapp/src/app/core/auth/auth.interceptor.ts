import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { OAuthService } from 'angular-oauth2-oidc';

/**
 * Single place responsible for attaching the bearer token: nothing else in the app (generated
 * API services included) sets an Authorization header itself.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const accessToken = inject(OAuthService).getAccessToken();
  if (!accessToken) {
    return next(req);
  }

  return next(req.clone({ setHeaders: { Authorization: `Bearer ${accessToken}` } }));
};
