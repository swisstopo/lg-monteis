import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { Observable, map } from 'rxjs';
import { PermissionsService } from './permissions.service';

export type AuthorizationCheck = (
  permissions: PermissionsService,
) => boolean | Promise<boolean> | Observable<boolean>;

export const ADMIN: AuthorizationCheck = (permissions) =>
  permissions.currentUser$.pipe(map((currentUser) => currentUser.isAdmin));

export function authorizationGuard(check: AuthorizationCheck): CanActivateFn {
  return () => check(inject(PermissionsService));
}
