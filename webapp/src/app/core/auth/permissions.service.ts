import { Injectable, computed, inject, signal } from '@angular/core';
import { CurrentUserControllerService, CurrentUserDto } from '@core/generated';
import { firstValueFrom } from 'rxjs';

/**
 * Single source for the cosmetic UI decisions (render a button or a menu entry, guard a route).
 * Loads `GET /api/me` once; the backend enforces every rule, this only mirrors it.
 *
 * Every signal fails closed: false or empty before {@link load} and after a failed call.
 */
@Injectable({ providedIn: 'root' })
export class PermissionsService {
  private readonly api = inject(CurrentUserControllerService);

  private readonly currentUser = signal<CurrentUserDto | undefined>(undefined);

  readonly isAdmin = computed(() => this.currentUser()?.isAdmin ?? false);

  readonly canWriteAllExperiments = computed(
    () => this.currentUser()?.canWriteAllExperiments ?? false,
  );

  readonly writeExperimentIds = computed<readonly string[]>(
    () => this.currentUser()?.writeExperimentIds ?? [],
  );

  readonly canAccessDocuments = computed(() => this.currentUser()?.canAccessDocuments ?? false);

  /** Whether the caller can write at least one experiment - decides whether a scoped write action is shown at all. */
  readonly hasAnyExperimentWriteAccess = computed(
    () => this.canWriteAllExperiments() || this.writeExperimentIds().length > 0,
  );

  /**
   * Run by the app initializer (provideAuth) and awaited there, so every permission is known before
   * the first route activates and the workbench starts: guards and the layout can read the signals
   * directly instead of waiting for the call.
   *
   * A failed call leaves the user `undefined`, so every permission stays closed; the HTTP error
   * itself is already surfaced to the user by the restErrorInterceptor.
   */
  async load(): Promise<void> {
    const currentUser = await firstValueFrom(this.api.getCurrentUser()).catch(() => undefined);
    this.currentUser.set(currentUser);
  }

  /**
   * Mirrors the backend Capabilities. `canWriteAllExperiments` already covers admins, so the admin
   * rule lives in the backend only.
   */
  canWriteExperiment(experimentId: string): boolean {
    return this.canWriteAllExperiments() || this.writeExperimentIds().includes(experimentId);
  }
}
