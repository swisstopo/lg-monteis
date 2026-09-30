import { Page } from '@playwright/test';

// Drives the real Keycloak-hosted login form the app redirects to when unauthenticated. One helper
// per seeded privilege level (docker/keycloak/realm/patch.local.json); every password equals the
// username.

// admin-user is in "/Monteis Admin" (MonteisAdmin): every action, including sensor management.
export async function loginAsAdmin(page: Page): Promise<void> {
  await login(page, 'admin-user', 'admin-user');
}

// editor-user is in "/Monteis Global Editor": may edit every experiment, but is no admin.
export async function loginAsGlobalEditor(page: Page): Promise<void> {
  await login(page, 'editor-user', 'editor-user');
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
