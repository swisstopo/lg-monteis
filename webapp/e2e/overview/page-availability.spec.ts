import { expect, test } from '@playwright/test';
import { loginAsAdmin } from '../support/login';

test.beforeEach(async ({ page }) => {
  await page.goto('http://localhost:4200/');
  await loginAsAdmin(page);

  await page.getByTitle('Measurements').click();
});

test('should have title', async ({ page }) => {
  await expect(page).toHaveTitle(/MONTEIS/);
});

test('should have usable search box', async ({ page }) => {
  // The beforeEach switched the active dock tab to "Measurements"; switch back to "Setup" to
  // reach the sensor table's search box.
  await page.getByTitle('Setup').click();
  await page.getByRole('link', { name: 'Sensor' }).click();

  const searchInput = page.getByRole('searchbox', { name: 'Search' });

  const searchText = 'Demo Search';

  await searchInput.fill(searchText);

  await expect(searchInput).toHaveValue(searchText);
});
