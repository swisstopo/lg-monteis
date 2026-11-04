import { EnvironmentInjector, runInInjectionContext } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { firstValueFrom, isObservable, Observable, of } from 'rxjs';
import { describe, expect, it } from 'vitest';
import { ADMIN, AuthorizationCheck, authorizationGuard } from './authorization.guard';
import { PermissionsService } from './permissions.service';

function runGuard(check: AuthorizationCheck, isAdmin = false): Promise<boolean> {
  TestBed.configureTestingModule({
    providers: [{ provide: PermissionsService, useValue: { currentUser$: of({ isAdmin }) } }],
  });
  const result = runInInjectionContext(TestBed.inject(EnvironmentInjector), () =>
    authorizationGuard(check)({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
  );
  return isObservable(result)
    ? firstValueFrom(result as Observable<boolean>)
    : Promise.resolve(result as boolean | Promise<boolean>);
}

describe('authorizationGuard', () => {
  it('passes the PermissionsService to the check and returns its answer', async () => {
    let received: PermissionsService | undefined;
    const check: AuthorizationCheck = (permissions) => {
      received = permissions;
      return false;
    };

    await expect(runGuard(check)).resolves.toBe(false);
    expect(received).toBe(TestBed.inject(PermissionsService));
  });

  it('lets an admin through ADMIN', async () => {
    await expect(runGuard(ADMIN, true)).resolves.toBe(true);
  });

  it('blocks a non-admin at ADMIN', async () => {
    await expect(runGuard(ADMIN, false)).resolves.toBe(false);
  });
});
