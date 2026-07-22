import {
  EnvironmentProviders,
  inject,
  makeEnvironmentProviders,
  provideAppInitializer,
} from '@angular/core';
import { OAuthService, provideOAuthClient } from 'angular-oauth2-oidc';
import { authConfig } from './auth.config';

export function provideAuth(): EnvironmentProviders {
  return makeEnvironmentProviders([
    provideOAuthClient(),
    provideAppInitializer(() => {
      const oauthService = inject(OAuthService);
      oauthService.configure(authConfig);
      return oauthService.loadDiscoveryDocumentAndTryLogin().then(() => {
        oauthService.setupAutomaticSilentRefresh();
      });
    }),
  ]);
}
