import { expect, test } from '@playwright/test';
import { loginAsAdmin } from '../support/login';

test.beforeEach(async ({ page }) => {
  await page.goto('http://localhost:4200/');
  await loginAsAdmin(page);

  await page.getByRole('link', { name: 'Sensor' }).click();
});

test('should create sensor', async ({ page }) => {
  await page.getByRole('button', { name: 'Create Sensor' }).click();
  await expect(page.getByRole('heading', { name: 'Setup new Sensor', level: 2 })).toBeVisible();

  // Several sensor table column headers reuse the same text as the dialog's field labels (e.g.
  // "Sensor Code", "Unit", "X (Local)"), so the table's filter inputs, now visible behind the
  // dialog, also match the generic page.getByLabel(...) substring match. Scope to the dialog.
  const dialog = page.getByRole('dialog');

  const uniqueId = crypto.randomUUID();
  // We need to make this ID unique due to the test running in parallel in different browsers.
  await dialog.getByLabel('Sensor Code').fill(`SN-TEMP-${uniqueId}`);
  await dialog.getByLabel('Sensor Name').fill('E2E TEST');

  await dialog.getByLabel('Unit').click();
  await page.getByRole('option', { name: 'Ampere (A)' }).click();

  await dialog.getByLabel('Sensor Type').fill('Temperature');
  await page.getByRole('option', { name: 'Temperature' }).click();

  await dialog.getByLabel('X (Local)').fill('100');
  await dialog.getByLabel('Y (Local)').fill('200');
  await dialog.getByLabel('Z (Local)').fill('300');

  await dialog.getByLabel('Alarm Limit From').fill('10');
  await dialog.getByLabel('Alarm Limit To').fill('100');

  await dialog.getByLabel("Formula Expression (Optional, defaults to 'x')").fill('x');
  await page.getByRole('option', { name: 'x * 1000 (v1)' }).click();

  await dialog.getByRole('button', { name: 'Save', exact: true }).click();

  await expect(page.getByText('Sensor saved successfully.')).toBeVisible();
});

test('should update sensor', async ({ page }) => {
  // "Edit Sensor" is only enabled once a row is selected.
  await page.locator('.ag-row').first().click();
  await page.getByRole('button', { name: 'Edit Sensor' }).click();
  await expect(page.getByRole('heading', { name: 'Edit Sensor', level: 2 })).toBeVisible();

  // Several sensor table column headers reuse the same text as the dialog's field labels (e.g.
  // "Sensor Code"), so the table's filter inputs, now visible behind the dialog, also match the
  // generic page.getByLabel(...) substring match. Scope to the dialog.
  const dialog = page.getByRole('dialog');

  const uniqueId = crypto.randomUUID();
  await dialog.getByLabel('Sensor Code').fill(`SN-TEMP-${uniqueId}`);
  await dialog.getByLabel('Sensor Name').fill('E2E TEST UPDATED');

  await dialog.getByLabel('Alarm Limit From').fill('20');
  await dialog.getByLabel('Alarm Limit To').fill('200');

  await dialog.getByLabel("Formula Expression (Optional, defaults to 'x')").fill('x');
  await page.getByRole('option', { name: 'x * 1000 (v1)' }).click();

  await dialog.getByRole('button', { name: 'Save', exact: true }).click();

  await expect(page.getByText('Sensor saved successfully.')).toBeVisible();
});

test('should fail to create existing sensor', async ({ page }) => {
  await page.getByRole('button', { name: 'Create Sensor' }).click();
  await expect(page.getByRole('heading', { name: 'Setup new Sensor', level: 2 })).toBeVisible();

  // Several sensor table column headers reuse the same text as the dialog's field labels (e.g.
  // "Sensor Code", "Unit", "X (Local)"), so the table's filter inputs, now visible behind the
  // dialog, also match the generic page.getByLabel(...) substring match. Scope to the dialog.
  const dialog = page.getByRole('dialog');

  const uniqueId = crypto.randomUUID();

  await dialog.getByLabel('Sensor Code').fill(`SN-TEMP-${uniqueId}`);
  await dialog.getByLabel('Sensor Name').fill('E2E TEST');

  await dialog.getByLabel('Unit').click();
  await page.getByRole('option', { name: 'Ampere (A)' }).click();

  await dialog.getByLabel('Sensor Type').fill('Temperature');
  await page.getByRole('option', { name: 'Temperature' }).click();

  await dialog.getByLabel('X (Local)').fill('100');
  await dialog.getByLabel('Y (Local)').fill('200');
  await dialog.getByLabel('Z (Local)').fill('300');

  await dialog.getByLabel('Alarm Limit From').fill('10');
  await dialog.getByLabel('Alarm Limit To').fill('100');

  await dialog.getByLabel("Formula Expression (Optional, defaults to 'x')").fill('x');
  await page.getByRole('option', { name: 'x * 1000 (v1)' }).click();

  await dialog.getByRole('button', { name: 'Save and create new', exact: true }).click();

  await expect(page.getByText('Sensor saved successfully.')).toBeVisible();

  await dialog.getByLabel('Sensor Code').fill(`SN-TEMP-${uniqueId}`);

  await dialog.getByLabel('Sensor Name').fill('E2E TEST 2');

  await dialog.getByLabel('Sensor Type').fill('Other');
  await page.getByRole('option', { name: 'Other' }).click();

  await dialog.getByLabel('X (Local)').fill('0');
  await dialog.getByLabel('Y (Local)').fill('10');
  await dialog.getByLabel('Z (Local)').fill('20');

  await dialog.getByLabel('Alarm Limit From').fill('10');
  await dialog.getByLabel('Alarm Limit To').fill('50');

  await dialog.getByRole('button', { name: 'Save', exact: true }).click();

  await expect(page.getByText('An entity with the same code already exists')).toBeVisible();
});

test('should show required validation errors', async ({ page }) => {
  await page.getByRole('button', { name: 'Create Sensor' }).click();
  await expect(page.getByRole('heading', { name: 'Setup new Sensor', level: 2 })).toBeVisible();

  // Scoped to the dialog: the sensor table's "Sensor Code" column filter input, now visible
  // behind the dialog, also matches the generic page.getByLabel(...) substring match otherwise.
  const dialog = page.getByRole('dialog');

  await dialog.getByLabel('Sensor Code').fill('x');
  await dialog.getByLabel('Sensor Code').fill('');
  await dialog.getByLabel('Sensor Code').blur();

  await expect(page.getByText('Sensor code is required')).toBeVisible();
  await expect(dialog.getByRole('button', { name: 'Save', exact: true })).toBeDisabled();
});

test('should reject invalid alarm limits', async ({ page }) => {
  await page.getByRole('button', { name: 'Create Sensor' }).click();

  // Scoped to the dialog: the sensor table's "Sensor Code" column filter input, now visible
  // behind the dialog, also matches the generic page.getByLabel(...) substring match otherwise.
  const dialog = page.getByRole('dialog');

  const uniqueId = crypto.randomUUID();

  await dialog.getByLabel('Sensor Code').fill(`SN-TEMP-${uniqueId}`);
  await dialog.getByLabel('Sensor Name').fill('E2E TEST');

  await dialog.getByLabel('Alarm Limit From').fill('100');
  await dialog.getByLabel('Alarm Limit To').fill('10');
  await dialog.getByLabel('Alarm Limit To').blur();

  await expect(page.getByText('This value must be higher than the lower limit')).toBeVisible();
});

test('should close dialog on cancel', async ({ page }) => {
  await page.getByRole('button', { name: 'Create Sensor' }).click();
  await page.getByRole('button', { name: 'Cancel' }).click();
  await expect(page.getByRole('heading', { name: 'Setup new Sensor', level: 2 })).not.toBeVisible();
});
