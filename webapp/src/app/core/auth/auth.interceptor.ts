import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { OAuthService } from 'angular-oauth2-oidc';

/**
 * Attaches the bearer token to every `HttpClient` request: nothing else in the app (generated
 * API services included) sets an Authorization header itself.
 *
 * The one exception is Giro3d, which fetches 3D tiles outside `HttpClient` and is therefore
 * handled separately in `tileset-auth.ts`.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const accessToken = inject(OAuthService).getAccessToken();
  if (!accessToken) {
    return next(req);
  }

  return next(req.clone({ setHeaders: { Authorization: `Bearer ${accessToken}` } }));
};
