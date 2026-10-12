import { describe, expect, it } from 'vitest';
import { missingOwnersKey } from './owners-status';

describe('missingOwnersKey', () => {
  it('has no reason while the owners are shown', () => {
    expect(missingOwnersKey('SHOWN')).toBeUndefined();
    expect(missingOwnersKey(undefined)).toBeUndefined();
  });

  it('names why the owners are missing', () => {
    expect(missingOwnersKey('KEYCLOAK_UNAVAILABLE')).toBe(
      'experiment.owners.status.KEYCLOAK_UNAVAILABLE',
    );
    expect(missingOwnersKey('ACCESS_DENIED')).toBe('experiment.owners.status.ACCESS_DENIED');
    expect(missingOwnersKey('NO_WRITE_GROUP')).toBe('experiment.owners.status.NO_WRITE_GROUP');
  });
});
