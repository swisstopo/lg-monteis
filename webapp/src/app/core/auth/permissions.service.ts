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
}
