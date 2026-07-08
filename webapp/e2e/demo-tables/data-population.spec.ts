import { expect, test } from '@playwright/test';

test('should find calculation data in table', async ({ page }) => {
  await page.goto('http://localhost:4200/');

  await page.getByRole('link', { name: 'AG-Grid Table' }).click();

  await expect(page.getByRole('columnheader', { name: 'Sensor ID' })).toBeVisible();
});

test('should find sensor date in table', async ({ page }) => {
  await page.goto('http://localhost:4200/');

  await page.getByRole('link', { name: 'Angular Material Table' }).click();

  await expect(page.getByRole('columnheader', { name: 'Timestamp' })).toBeVisible();
});
