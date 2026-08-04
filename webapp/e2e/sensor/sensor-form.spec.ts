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

  const uniqueId = crypto.randomUUID();
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

  const uniqueId = crypto.randomUUID();
  await page.getByLabel('Sensor Code').fill(`SN-TEMP-${uniqueId}`);
  await page.getByLabel('Sensor Name').fill('E2E TEST UPDATED');

  await page.getByLabel('Alarm Limit From').fill('20');
  await page.getByLabel('Alarm Limit To').fill('200');

  await page.getByLabel("Formula Expression (Optional, defaults to 'x')").fill('x');
  await page.getByRole('option', { name: 'x * 1000 (v1)' }).click();

  await page.getByRole('button', { name: 'Save', exact: true }).click();

  await expect(page.getByText('Sensor saved successfully.')).toBeVisible();
});

test('should fail to create existing sensor', async ({ page }) => {
  await page.getByRole('button', { name: 'Create Sensor' }).click();
  await expect(page.getByRole('heading', { name: 'Setup new Sensor', level: 2 })).toBeVisible();

  const uniqueId = crypto.randomUUID();

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

  await page.getByRole('button', { name: 'Save and create new', exact: true }).click();

  await expect(page.getByText('Sensor saved successfully.')).toBeVisible();

  await page.getByLabel('Sensor Code').fill(`SN-TEMP-${uniqueId}`);

  await page.getByLabel('Sensor Name').fill('E2E TEST 2');

  await page.getByLabel('Sensor Type').fill('Other');
  await page.getByRole('option', { name: 'Other' }).click();

  await page.getByLabel('X Local').fill('0');
  await page.getByLabel('Y Local').fill('10');
  await page.getByLabel('Z Local').fill('20');

  await page.getByLabel('Alarm Limit From').fill('10');
  await page.getByLabel('Alarm Limit To').fill('50');

  await page.getByRole('button', { name: 'Save', exact: true }).click();

  await expect(page.getByText('An entity with the same code already exists')).toBeVisible();
});

test('should show required validation errors', async ({ page }) => {
  await page.getByRole('button', { name: 'Create Sensor' }).click();
  await expect(page.getByRole('heading', { name: 'Setup new Sensor', level: 2 })).toBeVisible();

  await page.getByLabel('Sensor Code').fill('x');
  await page.getByLabel('Sensor Code').fill('');
  await page.getByLabel('Sensor Code').blur();

  await expect(page.getByText('Sensor code is required')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Save', exact: true })).toBeDisabled();
});

test('should reject invalid alarm limits', async ({ page }) => {
  await page.getByRole('button', { name: 'Create Sensor' }).click();

  const uniqueId = crypto.randomUUID();

  await page.getByLabel('Sensor Code').fill(`SN-TEMP-${uniqueId}`);
  await page.getByLabel('Sensor Name').fill('E2E TEST');

  await page.getByLabel('Alarm Limit From').fill('100');
  await page.getByLabel('Alarm Limit To').fill('10');
  await page.getByLabel('Alarm Limit To').blur();

  await expect(page.getByText('This value must be higher than the lower limit')).toBeVisible();
});

test('should close dialog on cancel', async ({ page }) => {
  await page.getByRole('button', { name: 'Create Sensor' }).click();
  await page.getByRole('button', { name: 'Cancel' }).click();
  await expect(page.getByRole('heading', { name: 'Setup new Sensor', level: 2 })).not.toBeVisible();
});
