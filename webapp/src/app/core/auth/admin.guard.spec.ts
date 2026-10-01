import { EnvironmentInjector, runInInjectionContext } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { firstValueFrom, Observable, of } from 'rxjs';
import { describe, expect, it } from 'vitest';
import { adminGuard } from './admin.guard';
import { PermissionsService } from './permissions.service';

function runGuard(isAdmin: boolean): Promise<boolean> {
  TestBed.configureTestingModule({
    providers: [{ provide: PermissionsService, useValue: { currentUser$: of({ isAdmin }) } }],
  });
  const result = runInInjectionContext(TestBed.inject(EnvironmentInjector), () =>
    adminGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
  );
  return firstValueFrom(result as Observable<boolean>);
}

describe('adminGuard', () => {
  it('lets an admin through', async () => {
    await expect(runGuard(true)).resolves.toBe(true);
  });

  it('blocks a non-admin', async () => {
    await expect(runGuard(false)).resolves.toBe(false);
  });
});
