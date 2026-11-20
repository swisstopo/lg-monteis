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

// missing param => deny: a global editor would pass canWrite with any id
export function writeExperiment(experimentIdParam: string): AuthorizationCheck {
  return (permissions, route) => {
    const experimentId = route.paramMap.get(experimentIdParam);
    if (!experimentId) return false;
    return permissions.currentUser$.pipe(map((currentUser) => canWrite(currentUser, experimentId)));
  };
}

export function authorizationGuard(check: AuthorizationCheck): CanActivateFn {
  return (route) => check(inject(PermissionsService), route);
}
