import { expect, test } from '@playwright/test';

test('should create sensor', async ({ page }) => {
  await page.goto('http://localhost:4200/');

  await page.getByRole('link', { name: 'Create Sensor' }).click();

  await expect(
    page.getByRole('heading', { name: 'Create New Sensor Configuration', level: 2 }),
  ).toBeVisible();

  const uniqueId = Date.now();
  // We need to make this ID unique due to the test running in parallel in different browsers.
  await page.getByLabel('Sensor Code').fill(`SN-TEMP-${uniqueId}`);
  await page.getByLabel('Sensor Name').fill('E2E TEST');

  await page.getByLabel('Lower Bound').fill('10');
  await page.getByLabel('Upper Bound').fill('100');

  await page.getByLabel("Formula Expression (Optional, defaults to 'x')").fill('x');
  await page.getByRole('option', { name: 'x * 1000 (v1)' }).click();

  await page.getByRole('button', { name: 'Save Configuration' }).click();

  await expect(
    page.getByRole('heading', { name: 'Server Response Success (Status: 201)', level: 3 }),
  ).toBeVisible();
});
