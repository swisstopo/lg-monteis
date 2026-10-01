import { expect, Page } from '@playwright/test';

const APP_URL = 'http://localhost:4200/';

// Drives the real Keycloak-hosted login form the app redirects to when unauthenticated. One helper
// per seeded privilege level (docker/keycloak/realm/patch.local.json); every password equals the
// username.

// admin-user is in "/Monteis Admin" (MonteisAdmin): every action, including sensor management.
export async function loginAsAdmin(page: Page): Promise<void> {
  await login(page, 'admin-user', 'admin-user');
}

// alice (ExperimentPI) is in "Experiment Alpha/read + write" and "Experiment Gamma/read".
export async function loginAsAlice(page: Page): Promise<void> {
  await login(page, 'alice', 'alice');
}

// bob (ExperimentUser) is in "Experiment Beta/read" only: reads Beta, writes nothing.
export async function loginAsBob(page: Page): Promise<void> {
  await login(page, 'bob', 'bob');
}

// basis-user (Basisrolle) is in no group: no experiment access and no write access.
export async function loginAsBasisUser(page: Page): Promise<void> {
  await login(page, 'basis-user', 'basis-user');
}

async function login(page: Page, username: string, password: string): Promise<void> {
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.locator('#kc-login').click();
}

/**
 * Opens the app, logs in with `loginAs`, and waits for the `/api/me` answer, so assertions that
 * something is absent cannot pass on the not-yet-loaded (fail-closed) state.
 */
export async function openAppAs(page: Page, loginAs: (page: Page) => Promise<void>): Promise<void> {
  const currentUser = page.waitForResponse(
    (response) => new URL(response.url()).pathname === '/api/me',
  );
  await page.goto(APP_URL);
  await loginAs(page);
  expect((await currentUser).ok()).toBe(true);
}
