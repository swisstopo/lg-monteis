import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { Observable, map } from 'rxjs';
import { PermissionsService } from './permissions.service';

/**
 * Decides whether the current user may open a route. A guard decides once per navigation, so a
 * check should wait for `/api/me` through `currentUser$` rather than read a signal.
 */
export type AuthorizationCheck = (
  permissions: PermissionsService,
) => boolean | Promise<boolean> | Observable<boolean>;

export const ADMIN: AuthorizationCheck = (permissions) =>
  permissions.currentUser$.pipe(map((currentUser) => currentUser.isAdmin));

/**
 * Lets the current user through when `check` passes. Only mirrors the backend, which still
 * enforces every endpoint behind the route.
 */
export function authorizationGuard(check: AuthorizationCheck): CanActivateFn {
  return () => check(inject(PermissionsService));
}
