import { AuthConfig } from 'angular-oauth2-oidc';

// issuer/clientId are injected at runtime from /env.json (see provide-auth.ts and
// public/env.json), so this build can point at different Keycloak instances (local dev, e2e
// Testcontainer, ...) without a rebuild.
//
// redirectUri/postLogoutRedirectUri are derived from window.location.origin (rather than
// hardcoded) so the same built bundle redirects back to whichever host it's actually served
// from (localhost, e2e, prod S3/CloudFront domain, ...) without a rebuild.
export const authConfig: AuthConfig = {
  redirectUri: window.location.origin,
  postLogoutRedirectUri: window.location.origin,
  responseType: 'code',
  scope: 'openid',
  showDebugInformation: true,
};
