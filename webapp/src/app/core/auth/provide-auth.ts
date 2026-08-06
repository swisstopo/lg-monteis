import {
  EnvironmentInjector,
  EnvironmentProviders,
  inject,
  makeEnvironmentProviders,
  provideAppInitializer,
  runInInjectionContext,
} from '@angular/core';
import { OAuthService, provideOAuthClient } from 'angular-oauth2-oidc';
import { authConfig } from './auth.config';
import { PermissionsService } from './permissions.service';
import { loadRuntimeEnv } from './runtime-env';

export function provideAuth(): EnvironmentProviders {
  return makeEnvironmentProviders([
    provideOAuthClient(),
    provideAppInitializer(async () => {
      const oauthService = inject(OAuthService);
      const injector = inject(EnvironmentInjector);
      const env = await loadRuntimeEnv();
      oauthService.configure({
        ...authConfig,
        issuer: env.keycloakIssuer,
        clientId: env.keycloakClientId,
      });
      await oauthService.loadDiscoveryDocumentAndTryLogin();
      oauthService.setupAutomaticSilentRefresh();
      if (oauthService.hasValidAccessToken()) {
        runInInjectionContext(injector, () => inject(PermissionsService));
      }
    }),
  ]);
}
