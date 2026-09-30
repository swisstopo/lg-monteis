import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { PermissionsService } from '@core/auth/permissions.service';

/**
 * Cosmetic route gate for admin-only views (the sensor table): waits for `/api/me` and sends
 * everyone who is not a confirmed admin - including when the call failed - to the measurements
 * overview. The backend still rejects every admin-only request on its own.
 */
export const adminGuard: CanActivateFn = async () => {
  const permissions = inject(PermissionsService);
  const router = inject(Router);

  const isAdmin = await permissions.resolveIsAdmin();
  return isAdmin ? true : router.createUrlTree(['/measurements-overview']);
};
