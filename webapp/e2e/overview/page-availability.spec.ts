import { expect, test } from '@playwright/test';
import { loginAsAdmin } from '../support/login';

test.beforeEach(async ({ page }) => {
  await page.goto('http://localhost:4200/');
  await loginAsAdmin(page);

  await page.getByRole('button', { name: 'data_exploration' }).click();
});

test('should have title', async ({ page }) => {
  await expect(page).toHaveTitle(/MONTEIS/);
});

test('should have usable search box', async ({ page }) => {
  await page.getByRole('link', { name: 'Test Dialog' }).click();

  const searchInput = page.getByRole('searchbox', { name: 'Search' });

  const searchText = 'Demo Search';

  await searchInput.fill(searchText);

  await expect(searchInput).toHaveValue(searchText);
});
