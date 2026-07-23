import { AuthConfig } from 'angular-oauth2-oidc';

// issuer/clientId are injected at runtime from /env.json (see provide-auth.ts and
// public/env.json), so this build can point at different Keycloak instances (local dev, e2e
// Testcontainer, ...) without a rebuild.
export const authConfig: AuthConfig = {
  redirectUri: 'http://localhost:4200',
  postLogoutRedirectUri: 'http://localhost:4200',
  responseType: 'code',
  scope: 'openid',
  showDebugInformation: true,
};
