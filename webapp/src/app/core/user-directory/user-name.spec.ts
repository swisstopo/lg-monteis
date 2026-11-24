import { describe, expect, it } from 'vitest';
import { userDisplayName } from './user-name';

describe('userDisplayName', () => {
  it('names a user by first and last name', () => {
    expect(userDisplayName({ id: 'anna', firstName: 'Anna', lastName: 'Arnold' })).toBe(
      'Anna Arnold',
    );
  });

  it('trims the name parts', () => {
    expect(userDisplayName({ id: 'anna', firstName: ' Anna ', lastName: ' Arnold' })).toBe(
      'Anna Arnold',
    );
  });

  it('names a user by the part that is there', () => {
    expect(userDisplayName({ id: 'anna', firstName: '  ', lastName: 'Arnold' })).toBe('Arnold');
  });

  it('names a user without a name by the e-mail', () => {
    expect(userDisplayName({ id: 'dora', email: 'dora@example.com' })).toBe('dora@example.com');
  });

  it('names a user without name and e-mail by the id', () => {
    expect(userDisplayName({ id: 'dora' })).toBe('dora');
  });
});
