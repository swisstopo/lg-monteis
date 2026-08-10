import { Page } from '@playwright/test';

// Drives the real Keycloak-hosted login form the app redirects to when unauthenticated. Uses the
// seeded admin-user (docker/keycloak/realm/patch.local.json) for now.
export async function loginAsAdmin(page: Page): Promise<void> {
  await login(page, 'admin-user', 'admin-user');
}

// The seeded bob user (docker/keycloak/realm/patch.local.json): only monteis-client:read via an
// experiment group, no write access used for verifying read-only behavior.
export async function loginAsReadOnlyUser(page: Page): Promise<void> {
  await login(page, 'bob', 'bob');
}

async function login(page: Page, username: string, password: string): Promise<void> {
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.locator('#kc-login').click();
}
