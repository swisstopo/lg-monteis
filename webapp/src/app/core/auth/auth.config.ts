import { AuthConfig } from 'angular-oauth2-oidc';

// Hardcoded for the single local dev setup (Keycloak from docker/compose.yml + the `monteis-spa`
// client from docker/keycloak/realm). Revisit once other stages exist.
export const authConfig: AuthConfig = {
  issuer: 'http://localhost:8081/auth/realms/monteis',
  clientId: 'monteis-spa',
  redirectUri: 'http://localhost:4200',
  postLogoutRedirectUri: 'http://localhost:4200',
  responseType: 'code',
  scope: 'openid',
  showDebugInformation: true,
};
