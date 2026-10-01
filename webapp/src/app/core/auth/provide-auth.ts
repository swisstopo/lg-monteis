import {
  EnvironmentInjector,
  EnvironmentProviders,
  inject,
  makeEnvironmentProviders,
  provideAppInitializer,
  runInInjectionContext,
} from '@angular/core';
import { OAuthService, provideOAuthClient } from 'angular-oauth2-oidc';
import { firstValueFrom } from 'rxjs';
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
        // created only after the login, the service checks the token when it requests /api/me.
        // awaited so the first render already shows the right menus and buttons
        const permissions = runInInjectionContext(injector, () => inject(PermissionsService));
        await firstValueFrom(permissions.currentUser$);
      }
    }),
  ]);
}
