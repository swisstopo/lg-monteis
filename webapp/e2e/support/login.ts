import { expect, Page } from '@playwright/test';
import { hasPath } from './responses';

export const APP_URL = 'http://localhost:4200/';

export interface SeedUser {
  username: string;
  password: string;
  level: string;
}

/**
 * One seeded user per privilege level (docker/keycloak/realm/patch.local.json). The realm file
 * holds their group memberships.
 */
export const SEED_USERS = {
  admin: { username: 'admin-user', password: 'admin-user', level: 'MonteisAdmin' },
  alice: { username: 'alice', password: 'alice', level: 'ExperimentPI' },
  bob: { username: 'bob', password: 'bob', level: 'ExperimentUser' },
  basisUser: { username: 'basis-user', password: 'basis-user', level: 'Basisrolle' },
} satisfies Record<string, SeedUser>;

/** How test titles name a seed user: its privilege level plus its username. */
export function label(user: SeedUser): string {
  return `${user.level} (${user.username})`;
}

/** Fills in the real Keycloak-hosted login form the app redirects to when unauthenticated. */
export async function login(page: Page, user: SeedUser): Promise<void> {
  await page.locator('#username').fill(user.username);
  await page.locator('#password').fill(user.password);
  await page.locator('#kc-login').click();
}

export async function loginAsAdmin(page: Page): Promise<void> {
  await login(page, SEED_USERS.admin);
}

/**
 * Opens the app, logs in as `user`, and waits for the `/api/me` answer, so assertions that
 * something is absent cannot pass on the not-yet-loaded (fail-closed) state.
 */
export async function openAppAs(page: Page, user: SeedUser): Promise<void> {
  const currentUser = page.waitForResponse((response) => hasPath(response, '/api/me'));
  await page.goto(APP_URL);
  await login(page, user);
  expect((await currentUser).ok()).toBe(true);
}
