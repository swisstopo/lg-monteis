import { Injectable, computed, inject, resource } from '@angular/core';
import { CurrentUserControllerService, CurrentUserDto } from '@core/generated';
import { firstValueFrom } from 'rxjs';

/**
 * Single source for the cosmetic UI decisions (render a button, a menu entry, allow a route).
 * Loads `GET /api/me` once; the backend enforces every rule, this only mirrors it.
 *
 * Every signal fails closed: false or empty before the call resolves and after it fails.
 */
@Injectable({ providedIn: 'root' })
export class PermissionsService {
  private readonly api = inject(CurrentUserControllerService);

  /**
   * The one `/api/me` call. A failure resolves to `undefined` so every permission stays closed;
   * the HTTP error itself is already surfaced to the user by the restErrorInterceptor.
   */
  private readonly currentUserRequest: Promise<CurrentUserDto | undefined> = firstValueFrom(
    this.api.getCurrentUser(),
  ).catch(() => undefined);

  private readonly currentUser = resource({
    loader: () => this.currentUserRequest,
  });

  readonly isAdmin = computed(() => this.currentUser.value()?.isAdmin ?? false);

  readonly canWriteAllExperiments = computed(
    () => this.currentUser.value()?.canWriteAllExperiments ?? false,
  );

  readonly writeExperimentIds = computed<readonly string[]>(
    () => this.currentUser.value()?.writeExperimentIds ?? [],
  );

  readonly canAccessDocuments = computed(
    () => this.currentUser.value()?.canAccessDocuments ?? false,
  );

  /** Whether the caller can write at least one experiment - decides whether a scoped write action is shown at all. */
  readonly hasAnyExperimentWriteAccess = computed(
    () => this.isAdmin() || this.canWriteAllExperiments() || this.writeExperimentIds().length > 0,
  );

  /** Mirrors the backend AccessPolicy: admins and write-all callers may write any experiment. */
  canWriteExperiment(experimentId: string): boolean {
    return (
      this.isAdmin() ||
      this.canWriteAllExperiments() ||
      this.writeExperimentIds().includes(experimentId)
    );
  }

  /**
   * Waits for the `/api/me` result and tells whether the caller is an admin. For route guards,
   * which must not decide on the not-yet-loaded (closed) state. False when the call failed.
   */
  async resolveIsAdmin(): Promise<boolean> {
    const currentUser = await this.currentUserRequest;
    return currentUser?.isAdmin ?? false;
  }
}
