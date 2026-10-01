import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { map } from 'rxjs';
import { PermissionsService } from './permissions.service';

/** Lets only a Monteis admin open the admin area. The backend still enforces every admin endpoint. */
export const adminGuard: CanActivateFn = () =>
  inject(PermissionsService).currentUser$.pipe(map((currentUser) => currentUser.isAdmin));
