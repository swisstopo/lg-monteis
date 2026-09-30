import { Injectable, computed, inject, resource } from '@angular/core';
import { CurrentUserControllerService, CurrentUserDto } from '@core/generated';
import { firstValueFrom } from 'rxjs';

/**
 * Single source for the cosmetic UI decisions (render a button or a menu entry).
 * Loads `GET /api/me` once; the backend enforces every rule, this only mirrors it.
 *
 * Every signal fails closed: false or empty before the call resolves and after it fails.
 */
@Injectable({ providedIn: 'root' })
export class PermissionsService {
  private readonly api = inject(CurrentUserControllerService);

  /**
   * A failed `/api/me` call resolves to `undefined`, so every permission stays closed; the HTTP
   * error itself is already surfaced to the user by the restErrorInterceptor.
   */
  private readonly currentUser = resource({
    loader: () =>
      firstValueFrom(this.api.getCurrentUser()).catch((): CurrentUserDto | undefined => undefined),
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
