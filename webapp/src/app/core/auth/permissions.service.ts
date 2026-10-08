import { Injectable, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { CurrentUserControllerService, CurrentUserDto } from '@core/generated';
import { OAuthService } from 'angular-oauth2-oidc';
import { Observable, catchError, defer, of, shareReplay } from 'rxjs';

const NO_PERMISSIONS: CurrentUserDto = {
  isAdmin: false,
  canWriteAllExperiments: false,
  writeExperimentIds: [],
  canAccessDocuments: false,
};

/**
 * Single source for the cosmetic UI decisions (render a button or a menu entry, guard a route).
 * Loads `GET /api/me` once; the backend enforces every rule, this only mirrors it.
 *
 * Every permission fails closed: {@link NO_PERMISSIONS} until the call answers and after it fails.
 */
@Injectable({ providedIn: 'root' })
export class PermissionsService {
  private readonly api = inject(CurrentUserControllerService);
  private readonly oauthService = inject(OAuthService);

  /**
   * no call without a token, it would only 401 and show the session expired toast while the login
   * redirect is running. a failed call falls back to {@link NO_PERMISSIONS}, the http error itself
   * is already shown by the restErrorInterceptor
   */
  readonly currentUser$: Observable<CurrentUserDto> = defer(() =>
    this.oauthService.hasValidAccessToken() ? this.api.getCurrentUser() : of(NO_PERMISSIONS),
  ).pipe(
    catchError(() => of(NO_PERMISSIONS)),
    shareReplay(1),
  );

  /** The admin role: write access to everything, including the admin-only actions and the Sensor menu. */
  readonly canWriteAll = computed(() => this.currentUser.value()?.canWriteAll ?? false);

  readonly writeExperimentIds = computed<readonly string[]>(
    () => this.currentUser().writeExperimentIds,
  );

  /** Whether the caller can write at least one experiment - decides whether a scoped write action is shown at all. */
  readonly hasAnyExperimentWriteAccess = computed(
    () => this.canWriteAll() || this.writeExperimentIds().length > 0,
  );

  /** Mirrors the backend write rule on MonteisAuthenticationToken. */
  canWriteExperiment(experimentId: string): boolean {
    return this.canWriteAll() || this.writeExperimentIds().includes(experimentId);
  }
}
