import { Injectable, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { CurrentUserControllerService, CurrentUserDto } from '@core/generated';
import { OAuthService } from 'angular-oauth2-oidc';
import { Observable, catchError, defer, of, shareReplay } from 'rxjs';

/** What a caller gets while `/api/me` has not answered, without a token and after a failed call. */
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
   * `/api/me`, requested once and replayed to every subscriber. One-time decisions (a guard, the
   * workbench layout) pipe this instead of reading a signal, so they wait for the answer rather
   * than deciding on the closed initial state.
   *
   * Without a token there is no call: it would only fail with 401 and show the session-expired
   * toast while the login redirect is under way. A failed call falls back to
   * {@link NO_PERMISSIONS}; the HTTP error itself is surfaced by the restErrorInterceptor.
   */
  readonly currentUser$: Observable<CurrentUserDto> = defer(() =>
    this.oauthService.hasValidAccessToken() ? this.api.getCurrentUser() : of(NO_PERMISSIONS),
  ).pipe(
    catchError(() => of(NO_PERMISSIONS)),
    shareReplay(1),
  );

  private readonly currentUser = toSignal(this.currentUser$, { initialValue: NO_PERMISSIONS });

  readonly isAdmin = computed(() => this.currentUser().isAdmin);

  readonly canWriteAllExperiments = computed(() => this.currentUser().canWriteAllExperiments);

  readonly writeExperimentIds = computed<readonly string[]>(
    () => this.currentUser().writeExperimentIds,
  );

  readonly canAccessDocuments = computed(() => this.currentUser().canAccessDocuments);

  /** Whether the caller can write at least one experiment - decides whether a scoped write action is shown at all. */
  readonly hasAnyExperimentWriteAccess = computed(
    () => this.canWriteAllExperiments() || this.writeExperimentIds().length > 0,
  );

  /**
   * Mirrors the backend Capabilities. `canWriteAllExperiments` already covers admins, so the admin
   * rule lives in the backend only.
   */
  canWriteExperiment(experimentId: string): boolean {
    return this.canWriteAllExperiments() || this.writeExperimentIds().includes(experimentId);
  }
}
