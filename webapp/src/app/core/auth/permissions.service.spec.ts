import { TestBed } from '@angular/core/testing';
import { CurrentUserControllerService, CurrentUserDto } from '@core/generated';
import { Observable, of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { PermissionsService } from './permissions.service';

function setup(getCurrentUser: () => Observable<CurrentUserDto>) {
  TestBed.configureTestingModule({
    providers: [
      PermissionsService,
      { provide: CurrentUserControllerService, useValue: { getCurrentUser } },
    ],
  });
  return TestBed.inject(PermissionsService);
}

describe('PermissionsService', () => {
  it('starts with canWrite false before the call resolves', () => {
    const service = setup(() => of({ canWrite: true }));

    expect(service.canWrite()).toBe(false);
  });

  it('reports canWrite true once the backend confirms write access', async () => {
    const service = setup(() => of({ canWrite: true }));

    await vi.waitFor(() => expect(service.canWrite()).toBe(true));
  });

  it('reports canWrite false for a read-only caller', async () => {
    const service = setup(() => of({ canWrite: false }));

    await vi.waitFor(() => expect(service.canWrite()).toBe(false));
  });

  it('fails closed (canWrite false) if the call errors', async () => {
    const service = setup(() => throwError(() => new Error('rejected')));

    await vi.waitFor(() => expect(service.canWrite()).toBe(false));
  });
});
