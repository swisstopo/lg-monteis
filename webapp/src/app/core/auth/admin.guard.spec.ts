import { EnvironmentInjector, runInInjectionContext } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { adminGuard } from './admin.guard';
import { PermissionsService } from './permissions.service';

function runGuard(isAdmin: boolean): unknown {
  TestBed.configureTestingModule({
    providers: [{ provide: PermissionsService, useValue: { isAdmin: () => isAdmin } }],
  });
  return runInInjectionContext(TestBed.inject(EnvironmentInjector), () =>
    adminGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
  );
}

describe('adminGuard', () => {
  it('lets an admin through', () => {
    expect(runGuard(true)).toBe(true);
  });

  it('blocks a non-admin', () => {
    expect(runGuard(false)).toBe(false);
  });
});
