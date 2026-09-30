import { TestBed } from '@angular/core/testing';
import { CurrentUserControllerService, CurrentUserDto } from '@core/generated';
import { Observable, of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { PermissionsService } from './permissions.service';

// DTOs per privilege level, mirroring the U2 seed users (docker/keycloak/realm/patch.local.json).
const PI_EXPERIMENT_ID = '00000000-0000-7000-8000-000000000301';
const OTHER_EXPERIMENT_ID = '00000000-0000-7000-8000-000000000303';

const NO_PRIVILEGES: CurrentUserDto = {
  isAdmin: false,
  canWriteAllExperiments: false,
  writeExperimentIds: [],
  canAccessDocuments: false,
};
const ADMIN: CurrentUserDto = {
  ...NO_PRIVILEGES,
  isAdmin: true,
  canWriteAllExperiments: true,
  canAccessDocuments: true,
};
const GLOBAL_EDITOR: CurrentUserDto = { ...NO_PRIVILEGES, canWriteAllExperiments: true };
const EXPERIMENT_PI: CurrentUserDto = { ...NO_PRIVILEGES, writeExperimentIds: [PI_EXPERIMENT_ID] };
const EXPERIMENT_USER: CurrentUserDto = NO_PRIVILEGES;

function setup(getCurrentUser: () => Observable<CurrentUserDto>) {
  TestBed.configureTestingModule({
    providers: [
      PermissionsService,
      { provide: CurrentUserControllerService, useValue: { getCurrentUser } },
    ],
  });
  return TestBed.inject(PermissionsService);
}

/**
 * Waits until the `/api/me` resource has settled, so assertions on "false" cannot pass on the
 * loading state. A failed call settles as `resolved` with no value (fail closed).
 */
async function settled(service: PermissionsService): Promise<void> {
  await vi.waitFor(() => expect(service['currentUser'].status()).toBe('resolved'));
}

describe('PermissionsService', () => {
  it('fails closed before the call resolves', () => {
    const service = setup(() => of(ADMIN));

    expect(service.isAdmin()).toBe(false);
    expect(service.canWriteAllExperiments()).toBe(false);
    expect(service.writeExperimentIds()).toEqual([]);
    expect(service.canAccessDocuments()).toBe(false);
    expect(service.hasAnyExperimentWriteAccess()).toBe(false);
    expect(service.canWriteExperiment(PI_EXPERIMENT_ID)).toBe(false);
  });

  it('fails closed when the call errors', async () => {
    const service = setup(() => throwError(() => new Error('rejected')));

    await settled(service);

    expect(service.isAdmin()).toBe(false);
    expect(service.canWriteAllExperiments()).toBe(false);
    expect(service.writeExperimentIds()).toEqual([]);
    expect(service.canAccessDocuments()).toBe(false);
    expect(service.hasAnyExperimentWriteAccess()).toBe(false);
    expect(service.canWriteExperiment(PI_EXPERIMENT_ID)).toBe(false);
  });

  it('grants an admin every action', async () => {
    const service = setup(() => of(ADMIN));

    await vi.waitFor(() => expect(service.isAdmin()).toBe(true));
    expect(service.hasAnyExperimentWriteAccess()).toBe(true);
    expect(service.canWriteExperiment(OTHER_EXPERIMENT_ID)).toBe(true);
  });

  it('lets a global editor write every experiment without being an admin', async () => {
    const service = setup(() => of(GLOBAL_EDITOR));

    await vi.waitFor(() => expect(service.canWriteAllExperiments()).toBe(true));
    expect(service.isAdmin()).toBe(false);
    expect(service.hasAnyExperimentWriteAccess()).toBe(true);
    expect(service.canWriteExperiment(OTHER_EXPERIMENT_ID)).toBe(true);
  });

  it('gives an experiment PI scoped write access through writeExperimentIds', async () => {
    const service = setup(() => of(EXPERIMENT_PI));

    await vi.waitFor(() => expect(service.writeExperimentIds()).toEqual([PI_EXPERIMENT_ID]));
    expect(service.isAdmin()).toBe(false);
    expect(service.canWriteAllExperiments()).toBe(false);
    expect(service.hasAnyExperimentWriteAccess()).toBe(true);
  });

  it('gives an ExperimentUser no write access at all', async () => {
    const service = setup(() => of(EXPERIMENT_USER));
    await settled(service);

    expect(service.isAdmin()).toBe(false);
    expect(service.canWriteAllExperiments()).toBe(false);
    expect(service.writeExperimentIds()).toEqual([]);
    expect(service.hasAnyExperimentWriteAccess()).toBe(false);
    expect(service.canWriteExperiment(PI_EXPERIMENT_ID)).toBe(false);
  });

  it('canWriteExperiment is true inside the id list and false outside it', async () => {
    const service = setup(() => of(EXPERIMENT_PI));

    await vi.waitFor(() => expect(service.canWriteExperiment(PI_EXPERIMENT_ID)).toBe(true));
    expect(service.canWriteExperiment(OTHER_EXPERIMENT_ID)).toBe(false);
  });

  it('reports canAccessDocuments from the backend flag', async () => {
    const withDocuments = setup(() => of({ ...NO_PRIVILEGES, canAccessDocuments: true }));

    await vi.waitFor(() => expect(withDocuments.canAccessDocuments()).toBe(true));
    expect(withDocuments.isAdmin()).toBe(false);
    expect(withDocuments.hasAnyExperimentWriteAccess()).toBe(false);
  });
});
