import { Injectable, computed, inject, resource } from '@angular/core';
import { CurrentUserControllerService } from '@core/generated';
import { firstValueFrom } from 'rxjs';

/**
 * Helper for cosmetic UI decision whether to render a Button or not. (READ/WRITE)
 */
@Injectable({ providedIn: 'root' })
export class PermissionsService {
  private readonly api = inject(CurrentUserControllerService);

  private readonly currentUser = resource({
    loader: () => firstValueFrom(this.api.getCurrentUser()),
  });

  readonly canWrite = computed(() => this.currentUser.value()?.canWrite ?? false);

  private readonly canWriteAllExperiments = computed(
    () => this.currentUser.value()?.canWriteAllExperiments ?? false,
  );
  private readonly writeExperimentIds = computed(
    () => this.currentUser.value()?.writeExperimentIds ?? [],
  );

  /** Whether the caller can write at least one experiment - use to decide whether to show a scoped write action at all. */
  readonly hasAnyExperimentWriteAccess = computed(
    () => this.canWrite() || this.canWriteAllExperiments() || this.writeExperimentIds().length > 0,
  );

  /** Mirrors ExperimentWriteAuthorizationManager: admins and write-all callers may write any experiment. */
  canWriteExperiment(experimentId: string): boolean {
    return this.canWriteAllExperiments() || this.writeExperimentIds().includes(experimentId);
  }
}
