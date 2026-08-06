import { expect, test } from '@playwright/test';
import { loginAsAdmin, loginAsReadOnlyUser } from '../support/login';

test('shows the write actions for a user with write permission', async ({ page }) => {
  await page.goto('http://localhost:4200/');
  await loginAsAdmin(page);
  await page.getByRole('link', { name: 'Sensor' }).click();

  await expect(page.getByRole('button', { name: 'Create Sensor' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Edit Sensor' })).toBeVisible();
});

test('hides the write actions for a read-only user', async ({ page }) => {
  await page.goto('http://localhost:4200/');
  await loginAsReadOnlyUser(page);
  await page.getByRole('link', { name: 'Sensor' }).click();
  // download is always rendered.
  await expect(page.getByRole('button', { name: 'Download' })).toBeVisible();

  await expect(page.getByRole('button', { name: 'Create Sensor' })).not.toBeVisible();
  await expect(page.getByRole('button', { name: 'Edit Sensor' })).not.toBeVisible();
});
