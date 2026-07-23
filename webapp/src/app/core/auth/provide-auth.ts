import {
  EnvironmentProviders,
  inject,
  makeEnvironmentProviders,
  provideAppInitializer,
} from '@angular/core';
import { OAuthService, provideOAuthClient } from 'angular-oauth2-oidc';
import { authConfig } from './auth.config';
import { loadRuntimeEnv } from './runtime-env';

export function provideAuth(): EnvironmentProviders {
  return makeEnvironmentProviders([
    provideOAuthClient(),
    provideAppInitializer(async () => {
      const oauthService = inject(OAuthService);
      const env = await loadRuntimeEnv();
      oauthService.configure({
        ...authConfig,
        issuer: env.keycloakIssuer,
        clientId: env.keycloakClientId,
      });
      await oauthService.loadDiscoveryDocumentAndTryLogin();
      oauthService.setupAutomaticSilentRefresh();
    }),
  ]);
}
