import { TestBed } from '@angular/core/testing';
import { CurrentUserControllerService, CurrentUserDto } from '@core/generated';
import { OAuthService } from 'angular-oauth2-oidc';
import { firstValueFrom, NEVER, Observable, of, throwError } from 'rxjs';
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

function setup(getCurrentUser: () => Observable<CurrentUserDto>, hasValidAccessToken = true) {
  TestBed.configureTestingModule({
    providers: [
      PermissionsService,
      { provide: CurrentUserControllerService, useValue: { getCurrentUser } },
      { provide: OAuthService, useValue: { hasValidAccessToken: () => hasValidAccessToken } },
    ],
  });
  return TestBed.inject(PermissionsService);
}

describe('PermissionsService', () => {
  it('fails closed before the call answers', () => {
    const service = setup(() => NEVER);

    expect(service.isAdmin()).toBe(false);
    expect(service.canWriteAllExperiments()).toBe(false);
    expect(service.writeExperimentIds()).toEqual([]);
    expect(service.canAccessDocuments()).toBe(false);
    expect(service.hasAnyExperimentWriteAccess()).toBe(false);
    expect(service.canWriteExperiment(PI_EXPERIMENT_ID)).toBe(false);
  });

  it('fails closed when the call errors', () => {
    const service = setup(() => throwError(() => new Error('rejected')));

    expect(service.isAdmin()).toBe(false);
    expect(service.canWriteAllExperiments()).toBe(false);
    expect(service.writeExperimentIds()).toEqual([]);
    expect(service.canAccessDocuments()).toBe(false);
    expect(service.hasAnyExperimentWriteAccess()).toBe(false);
    expect(service.canWriteExperiment(PI_EXPERIMENT_ID)).toBe(false);
  });

  it('grants an admin every action', () => {
    const service = setup(() => of(ADMIN));

    expect(service.isAdmin()).toBe(true);
    expect(service.hasAnyExperimentWriteAccess()).toBe(true);
    expect(service.canWriteExperiment(OTHER_EXPERIMENT_ID)).toBe(true);
  });

  it('lets a global editor write every experiment without being an admin', () => {
    const service = setup(() => of(GLOBAL_EDITOR));

    expect(service.canWriteAllExperiments()).toBe(true);
    expect(service.isAdmin()).toBe(false);
    expect(service.hasAnyExperimentWriteAccess()).toBe(true);
    expect(service.canWriteExperiment(OTHER_EXPERIMENT_ID)).toBe(true);
  });

  it('gives an experiment PI scoped write access through writeExperimentIds', () => {
    const service = setup(() => of(EXPERIMENT_PI));

    expect(service.writeExperimentIds()).toEqual([PI_EXPERIMENT_ID]);
    expect(service.isAdmin()).toBe(false);
    expect(service.canWriteAllExperiments()).toBe(false);
    expect(service.hasAnyExperimentWriteAccess()).toBe(true);
  });

  it('gives an ExperimentUser no write access at all', () => {
    const service = setup(() => of(EXPERIMENT_USER));

    expect(service.isAdmin()).toBe(false);
    expect(service.canWriteAllExperiments()).toBe(false);
    expect(service.writeExperimentIds()).toEqual([]);
    expect(service.hasAnyExperimentWriteAccess()).toBe(false);
    expect(service.canWriteExperiment(PI_EXPERIMENT_ID)).toBe(false);
  });

  it('canWriteExperiment is true inside the id list and false outside it', () => {
    const service = setup(() => of(EXPERIMENT_PI));

    expect(service.canWriteExperiment(PI_EXPERIMENT_ID)).toBe(true);
    expect(service.canWriteExperiment(OTHER_EXPERIMENT_ID)).toBe(false);
  });

  it('reports canAccessDocuments from the backend flag', () => {
    const withDocuments = setup(() => of({ ...NO_PRIVILEGES, canAccessDocuments: true }));

    expect(withDocuments.canAccessDocuments()).toBe(true);
    expect(withDocuments.isAdmin()).toBe(false);
    expect(withDocuments.hasAnyExperimentWriteAccess()).toBe(false);
  });

  it('does not call /api/me without a token', async () => {
    const getCurrentUser = vi.fn(() => of(ADMIN));
    const service = setup(getCurrentUser, false);

    await expect(firstValueFrom(service.currentUser$)).resolves.toEqual(NO_PRIVILEGES);
    expect(getCurrentUser).not.toHaveBeenCalled();
    expect(service.isAdmin()).toBe(false);
  });

  it('calls /api/me once and replays the answer to every subscriber', async () => {
    const getCurrentUser = vi.fn(() => of(ADMIN));
    const service = setup(getCurrentUser);

    await expect(firstValueFrom(service.currentUser$)).resolves.toEqual(ADMIN);
    await expect(firstValueFrom(service.currentUser$)).resolves.toEqual(ADMIN);
    expect(getCurrentUser).toHaveBeenCalledTimes(1);
  });

  it('currentUser$ answers with no permissions when the call errors', async () => {
    const service = setup(() => throwError(() => new Error('rejected')));

    await expect(firstValueFrom(service.currentUser$)).resolves.toEqual(NO_PRIVILEGES);
  });
});
