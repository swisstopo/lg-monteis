import { EnvironmentInjector, runInInjectionContext } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, convertToParamMap, RouterStateSnapshot } from '@angular/router';
import { CurrentUserDto } from '@core/generated';
import { firstValueFrom, isObservable, Observable, of } from 'rxjs';
import { describe, expect, it } from 'vitest';
import {
  ADMIN,
  AuthorizationCheck,
  authorizationGuard,
  writeExperiment,
} from './authorization.guard';
import { PermissionsService } from './permissions.service';

const READER: CurrentUserDto = {
  isAdmin: false,
  canWriteAllExperiments: false,
  writeExperimentIds: [],
  canAccessDocuments: false,
};

function runGuard(
  check: AuthorizationCheck,
  user: Partial<CurrentUserDto> = {},
  params: Record<string, string> = {},
): Promise<boolean> {
  TestBed.configureTestingModule({
    providers: [
      { provide: PermissionsService, useValue: { currentUser$: of({ ...READER, ...user }) } },
    ],
  });
  const route = { paramMap: convertToParamMap(params) } as ActivatedRouteSnapshot;
  const result = runInInjectionContext(TestBed.inject(EnvironmentInjector), () =>
    authorizationGuard(check)(route, {} as RouterStateSnapshot),
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
    await expect(runGuard(ADMIN, { isAdmin: true })).resolves.toBe(true);
  });

  it('blocks a non-admin at ADMIN', async () => {
    await expect(runGuard(ADMIN, { isAdmin: false })).resolves.toBe(false);
  });

  it('passes the route to the check', async () => {
    let received: ActivatedRouteSnapshot | undefined;
    const check: AuthorizationCheck = (_permissions, route) => {
      received = route;
      return true;
    };

    await runGuard(check, {}, { experimentId: 'x' });

    expect(received?.paramMap.get('experimentId')).toBe('x');
  });

  it('lets a PI of the experiment in the route through writeExperiment', async () => {
    const pi = { writeExperimentIds: ['x'] };

    await expect(
      runGuard(writeExperiment('experimentId'), pi, { experimentId: 'x' }),
    ).resolves.toBe(true);
  });

  it('lets a user who can write every experiment through writeExperiment', async () => {
    const globalEditor = { canWriteAllExperiments: true };

    await expect(
      runGuard(writeExperiment('experimentId'), globalEditor, { experimentId: 'x' }),
    ).resolves.toBe(true);
  });

  it('blocks a PI of another experiment at writeExperiment', async () => {
    const piOfY = { writeExperimentIds: ['y'] };

    await expect(
      runGuard(writeExperiment('experimentId'), piOfY, { experimentId: 'x' }),
    ).resolves.toBe(false);
  });

  it('blocks a reader at writeExperiment', async () => {
    await expect(
      runGuard(writeExperiment('experimentId'), {}, { experimentId: 'x' }),
    ).resolves.toBe(false);
  });

  it('blocks a user who can write every experiment when the route param is missing', async () => {
    const globalEditor = { canWriteAllExperiments: true };

    await expect(runGuard(writeExperiment('experimentId'), globalEditor, {})).resolves.toBe(false);
  });
});
