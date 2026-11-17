import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn } from '@angular/router';
import { map, Observable } from 'rxjs';
import { canWrite, PermissionsService } from './permissions.service';

export type AuthorizationCheck = (
  permissions: PermissionsService,
  route: ActivatedRouteSnapshot,
) => boolean | Promise<boolean> | Observable<boolean>;

export const ADMIN: AuthorizationCheck = (permissions) =>
  permissions.currentUser$.pipe(map((currentUser) => currentUser.isAdmin));

export const WRITE_EXPERIMENT: AuthorizationCheck = (permissions, route) =>
  permissions.currentUser$.pipe(
    map((currentUser) => canWrite(currentUser, route.paramMap.get('experimentId') ?? '')),
  );

export function authorizationGuard(check: AuthorizationCheck): CanActivateFn {
  return (route) => check(inject(PermissionsService), route);
}
