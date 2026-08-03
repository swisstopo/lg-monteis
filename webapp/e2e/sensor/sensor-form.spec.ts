import { expect, test } from '@playwright/test';
import { loginAsAdmin } from '../support/login';

test.beforeEach(async ({ page }) => {
  await page.goto('http://localhost:4200/');
  await loginAsAdmin(page);

  await page.getByRole('button', { name: 'data_exploration' }).click();
  await page.getByRole('link', { name: 'Test Dialog' }).click();
});

test('should create sensor', async ({ page }) => {
  await page.getByRole('button', { name: 'Create Sensor' }).click();
  await expect(page.getByRole('heading', { name: 'Setup new Sensor', level: 2 })).toBeVisible();

  const uniqueId = Date.now();
  // We need to make this ID unique due to the test running in parallel in different browsers.
  await page.getByLabel('Sensor Code').fill(`SN-TEMP-${uniqueId}`);
  await page.getByLabel('Sensor Name').fill('E2E TEST');

  await page.getByLabel('Unit').click();
  await page.getByRole('option', { name: 'Ampere (A)' }).click();

  await page.getByLabel('Sensor Type').fill('Temperature');
  await page.getByRole('option', { name: 'Temperature' }).click();

  await page.getByLabel('X Local').fill('100');
  await page.getByLabel('Y Local').fill('200');
  await page.getByLabel('Z Local').fill('300');

  await page.getByLabel('Alarm Limit From').fill('10');
  await page.getByLabel('Alarm Limit To').fill('100');

  await page.getByLabel("Formula Expression (Optional, defaults to 'x')").fill('x');
  await page.getByRole('option', { name: 'x * 1000 (v1)' }).click();

  await page.getByRole('button', { name: 'Save', exact: true }).click();

  await expect(page.getByText('Sensor saved successfully.')).toBeVisible();
});

test('should update sensor', async ({ page }) => {
  await page.getByRole('button', { name: 'Edit Sensor' }).click();
  await expect(page.getByRole('heading', { name: 'Edit Sensor', level: 2 })).toBeVisible();

  const uniqueId = Date.now();
  await page.getByLabel('Sensor Code').fill(`SN-TEMP-${uniqueId}`);
  await page.getByLabel('Sensor Name').fill('E2E TEST UPDATED');

  await page.getByLabel('Alarm Limit From').fill('20');
  await page.getByLabel('Alarm Limit To').fill('200');

  await page.getByLabel("Formula Expression (Optional, defaults to 'x')").fill('x');
  await page.getByRole('option', { name: 'x * 1000 (v1)' }).click();

  await page.getByRole('button', { name: 'Save', exact: true }).click();

  await expect(page.getByText('Sensor saved successfully.')).toBeVisible();
});
