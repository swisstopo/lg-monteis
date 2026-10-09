import { expect, test } from '@playwright/test';
import { openAppAs, SEED_USERS } from '../support/login';

test.beforeEach(async ({ page }) => {
  await openAppAs(page, SEED_USERS.admin);

  await page.getByTitle('Measurements').click();
});

test('should find calculation data in table', async ({ page }) => {
  await page.getByRole('link', { name: 'Table' }).click();

  await expect(page.getByRole('columnheader', { name: 'DAS Key' })).toBeVisible();
});

test('should find sensor date in table', async ({ page }) => {
  await page.getByRole('link', { name: 'Table' }).click();

  await expect(page.getByRole('columnheader', { name: 'Newest Measurement' })).toBeVisible();
});
