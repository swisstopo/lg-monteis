import { Page } from '@playwright/test';

// Drives the real Keycloak-hosted login form the app redirects to when unauthenticated. Uses the
// seeded admin-user (docker/keycloak/realm/patch.local.json) for now.
export async function loginAsAdmin(page: Page): Promise<void> {
  await page.locator('#username').fill('admin-user');
  await page.locator('#password').fill('admin-user');
  await page.locator('#kc-login').click();
}
