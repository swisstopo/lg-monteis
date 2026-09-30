import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  GuardResult,
  MaybeAsync,
  provideRouter,
  Router,
  RouterStateSnapshot,
} from '@angular/router';
import { CurrentUserControllerService, CurrentUserDto } from '@core/generated';
import { Observable, of, Subject, throwError } from 'rxjs';
import { describe, expect, it } from 'vitest';
import { adminGuard } from './admin.guard';

const NO_PRIVILEGES: CurrentUserDto = {
  isAdmin: false,
  canWriteAllExperiments: false,
  writeExperimentIds: [],
  canAccessDocuments: false,
};

function setup(getCurrentUser: () => Observable<CurrentUserDto>) {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: CurrentUserControllerService, useValue: { getCurrentUser } },
    ],
  });
}

function runGuard(): MaybeAsync<GuardResult> {
  return TestBed.runInInjectionContext(() =>
    adminGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
  );
}

function redirectToOverview() {
  return TestBed.inject(Router).createUrlTree(['/measurements-overview']);
}

describe('adminGuard', () => {
  it('allows an admin', async () => {
    setup(() => of({ ...NO_PRIVILEGES, isAdmin: true, canWriteAllExperiments: true }));

    await expect(runGuard()).resolves.toBe(true);
  });

  it('redirects a non-admin (global editor) to the measurements overview', async () => {
    setup(() => of({ ...NO_PRIVILEGES, canWriteAllExperiments: true }));

    await expect(runGuard()).resolves.toEqual(redirectToOverview());
  });

  it('redirects to the measurements overview when /api/me fails', async () => {
    setup(() => throwError(() => new Error('rejected')));

    await expect(runGuard()).resolves.toEqual(redirectToOverview());
  });

  it('waits for a slow /api/me instead of deciding on the not-yet-loaded state', async () => {
    const response = new Subject<CurrentUserDto>();
    setup(() => response);

    let result: GuardResult | undefined;
    const pending = Promise.resolve(runGuard()).then((value) => (result = value as GuardResult));
    // Let every queued microtask and a macrotask run: the guard must still be waiting.
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(result).toBeUndefined();

    response.next({ ...NO_PRIVILEGES, isAdmin: true });
    response.complete();
    await pending;

    expect(result).toBe(true);
  });
});
