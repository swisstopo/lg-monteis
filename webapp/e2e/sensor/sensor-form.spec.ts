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
  // "DAS Sensor Alias", "Unit", "X (Local)"), so the table's filter inputs, now visible behind the
  // dialog, also match the generic page.getByLabel(...) substring match. Scope to the dialog.
  const dialog = page.getByRole('dialog');

  const uniqueId = crypto.randomUUID();
  // We need to make this ID unique due to the test running in parallel in different browsers.
  await dialog.getByLabel('DAS Sensor Alias').fill(`SN-TEMP-${uniqueId}`);
  await dialog.getByLabel('Sensor Name').fill('E2E TEST');

  // 'DAS' alone substring-matches 'DAS Sensor Alias' and 'DAS Parameter Alias' too - scope exactly.
  await dialog.getByLabel('DAS', { exact: true }).click();
  await page.getByRole('option', { name: 'SolExperts' }).click();

  const firstParameter = dialog.getByTestId('parameter-block-0');
  await firstParameter.getByLabel('Parameter Name').fill('Temperature Reading');

  await firstParameter.getByLabel('Unit').click();
  await page.getByRole('option', { name: 'Ampere (A)' }).click();

  await firstParameter.getByLabel('Sensor Type').fill('Temperature');
  await page.getByRole('option', { name: 'Temperature' }).click();

  await dialog.getByLabel('X (Local)').fill('100');
  await dialog.getByLabel('Y (Local)').fill('200');
  await dialog.getByLabel('Z (Local)').fill('300');

  await firstParameter.getByLabel('Alarm Limit From').fill('10');
  await firstParameter.getByLabel('Alarm Limit To').fill('100');

  // The CDK overlay is position: fixed and can render outside the actual viewport (WebKit gives
  // it a zero-overlap bounding box), so no click - mouse or forced - has coordinates to land on.
  // Typing the exact expression narrows the autocomplete to this one option and Enter selects it
  // via the keyboard instead, which is independent of the overlay's on-screen position.
  // getByLabel matches both the input and its results listbox here - Material's autocomplete
  // gives them the same aria-labelledby. The combobox role is unique to the input.
  const formulaInput = firstParameter.getByRole('combobox', {
    name: "Formula Expression (Optional, defaults to 'x')",
  });
  await formulaInput.fill('x * 1000');
  await expect(page.getByRole('option', { name: 'x * 1000 (v1)' })).toBeVisible();
  await formulaInput.press('ArrowDown');
  await formulaInput.press('Enter');

  await dialog.getByRole('button', { name: 'Save', exact: true }).click();

  await expect(page.getByText('Sensor saved successfully.')).toBeVisible();
});

test('should update sensor', async ({ page }) => {
  // "Edit Sensor" is only enabled once a row is selected. Infinite row model: the row initially
  // renders as an empty placeholder while its data block loads. Clicking too early hits a
  // not-yet-loaded node, which ag-grid silently ignores for selection - wait for real content
  // before clicking.
  const firstRow = page.locator('.ag-row').first();
  await expect(firstRow.locator('[col-id="dasSensorAlias"]')).not.toBeEmpty();
  await firstRow.click();
  await page.getByRole('button', { name: 'Edit Sensor' }).click();
  await expect(page.getByRole('heading', { name: 'Edit Sensor', level: 2 })).toBeVisible();

  // Several sensor table column headers reuse the same text as the dialog's field labels (e.g.
  // "DAS Sensor Alias"), so the table's filter inputs, now visible behind the dialog, also match
  // the generic page.getByLabel(...) substring match. Scope to the dialog.
  const dialog = page.getByRole('dialog');

  const uniqueId = crypto.randomUUID();
  await dialog.getByLabel('DAS Sensor Alias').fill(`SN-TEMP-${uniqueId}`);
  await dialog.getByLabel('Sensor Name').fill('E2E TEST UPDATED');

  const firstParameter = dialog.getByTestId('parameter-block-0');
  await firstParameter.getByLabel('Alarm Limit From').fill('20');
  await firstParameter.getByLabel('Alarm Limit To').fill('200');

  // The CDK overlay is position: fixed and can render outside the actual viewport (WebKit gives
  // it a zero-overlap bounding box), so no click - mouse or forced - has coordinates to land on.
  // Typing the exact expression narrows the autocomplete to this one option and Enter selects it
  // via the keyboard instead, which is independent of the overlay's on-screen position.
  // getByLabel matches both the input and its results listbox here - Material's autocomplete
  // gives them the same aria-labelledby. The combobox role is unique to the input.
  const formulaInput = firstParameter.getByRole('combobox', {
    name: "Formula Expression (Optional, defaults to 'x')",
  });
  await formulaInput.fill('x * 1000');
  await expect(page.getByRole('option', { name: 'x * 1000 (v1)' })).toBeVisible();
  await formulaInput.press('ArrowDown');
  await formulaInput.press('Enter');

  await dialog.getByRole('button', { name: 'Save', exact: true }).click();

  await expect(page.getByText('Sensor saved successfully.')).toBeVisible();
});

test('should fail to create existing sensor', async ({ page }) => {
  await page.getByRole('button', { name: 'Create Sensor' }).click();
  await expect(page.getByRole('heading', { name: 'Setup new Sensor', level: 2 })).toBeVisible();

  // Several sensor table column headers reuse the same text as the dialog's field labels (e.g.
  // "DAS Sensor Alias", "Unit", "X (Local)"), so the table's filter inputs, now visible behind the
  // dialog, also match the generic page.getByLabel(...) substring match. Scope to the dialog.
  const dialog = page.getByRole('dialog');

  const uniqueId = crypto.randomUUID();

  await dialog.getByLabel('DAS Sensor Alias').fill(`SN-TEMP-${uniqueId}`);
  await dialog.getByLabel('Sensor Name').fill('E2E TEST');

  // 'DAS' alone substring-matches 'DAS Sensor Alias' and 'DAS Parameter Alias' too - scope exactly.
  await dialog.getByLabel('DAS', { exact: true }).click();
  await page.getByRole('option', { name: 'SolExperts' }).click();

  const firstParameter = dialog.getByTestId('parameter-block-0');
  await firstParameter.getByLabel('Parameter Name').fill('Temperature Reading');

  await firstParameter.getByLabel('Unit').click();
  await page.getByRole('option', { name: 'Ampere (A)' }).click();

  await firstParameter.getByLabel('Sensor Type').fill('Temperature');
  await page.getByRole('option', { name: 'Temperature' }).click();

  await dialog.getByLabel('X (Local)').fill('100');
  await dialog.getByLabel('Y (Local)').fill('200');
  await dialog.getByLabel('Z (Local)').fill('300');

  await firstParameter.getByLabel('Alarm Limit From').fill('10');
  await firstParameter.getByLabel('Alarm Limit To').fill('100');

  // The CDK overlay is position: fixed and can render outside the actual viewport (WebKit gives
  // it a zero-overlap bounding box), so no click - mouse or forced - has coordinates to land on.
  // Typing the exact expression narrows the autocomplete to this one option and Enter selects it
  // via the keyboard instead, which is independent of the overlay's on-screen position.
  // getByLabel matches both the input and its results listbox here - Material's autocomplete
  // gives them the same aria-labelledby. The combobox role is unique to the input.
  const formulaInput = firstParameter.getByRole('combobox', {
    name: "Formula Expression (Optional, defaults to 'x')",
  });
  await formulaInput.fill('x * 1000');
  await expect(page.getByRole('option', { name: 'x * 1000 (v1)' })).toBeVisible();
  await formulaInput.press('ArrowDown');
  await formulaInput.press('Enter');

  await dialog.getByRole('button', { name: 'Save and create new', exact: true }).click();

  await expect(page.getByText('Sensor saved successfully.')).toBeVisible();

  await dialog.getByLabel('DAS Sensor Alias').fill(`SN-TEMP-${uniqueId}`);

  await dialog.getByLabel('Sensor Name').fill('E2E TEST 2');

  await firstParameter.getByLabel('Parameter Name').fill('Temperature Reading 2');

  await firstParameter.getByLabel('Sensor Type').fill('Other');
  await page.getByRole('option', { name: 'Other' }).click();

  await dialog.getByLabel('X (Local)').fill('0');
  await dialog.getByLabel('Y (Local)').fill('10');
  await dialog.getByLabel('Z (Local)').fill('20');

  await firstParameter.getByLabel('Alarm Limit From').fill('10');
  await firstParameter.getByLabel('Alarm Limit To').fill('50');

  await dialog.getByRole('button', { name: 'Save', exact: true }).click();

  await expect(page.getByText('An entity with the same code already exists')).toBeVisible();
});

test('should show required validation errors', async ({ page }) => {
  await page.getByRole('button', { name: 'Create Sensor' }).click();
  await expect(page.getByRole('heading', { name: 'Setup new Sensor', level: 2 })).toBeVisible();

  // Scoped to the dialog: the sensor table's "DAS Sensor Alias" column filter input, now visible
  // behind the dialog, also matches the generic page.getByLabel(...) substring match otherwise.
  const dialog = page.getByRole('dialog');

  await dialog.getByLabel('DAS Sensor Alias').fill('x');
  await dialog.getByLabel('DAS Sensor Alias').fill('');
  await dialog.getByLabel('DAS Sensor Alias').blur();

  await expect(page.getByText('DAS sensor alias is required')).toBeVisible();

  const firstParameter = dialog.getByTestId('parameter-block-0');
  await firstParameter.getByLabel('Parameter Name').fill('x');
  await firstParameter.getByLabel('Parameter Name').fill('');
  await firstParameter.getByLabel('Parameter Name').blur();

  await expect(page.getByText('Parameter name is required')).toBeVisible();
  await expect(dialog.getByRole('button', { name: 'Save', exact: true })).toBeDisabled();
});

test('should reject invalid alarm limits', async ({ page }) => {
  await page.getByRole('button', { name: 'Create Sensor' }).click();

  // Scoped to the dialog: the sensor table's "DAS Sensor Alias" column filter input, now visible
  // behind the dialog, also matches the generic page.getByLabel(...) substring match otherwise.
  const dialog = page.getByRole('dialog');

  const uniqueId = crypto.randomUUID();

  await dialog.getByLabel('DAS Sensor Alias').fill(`SN-TEMP-${uniqueId}`);
  await dialog.getByLabel('Sensor Name').fill('E2E TEST');

  const firstParameter = dialog.getByTestId('parameter-block-0');
  await firstParameter.getByLabel('Alarm Limit From').fill('100');
  await firstParameter.getByLabel('Alarm Limit To').fill('10');
  await firstParameter.getByLabel('Alarm Limit To').blur();

  await expect(page.getByText('This value must be higher than the lower limit')).toBeVisible();
});

test('should close dialog on cancel', async ({ page }) => {
  await page.getByRole('button', { name: 'Create Sensor' }).click();
  await page.getByRole('button', { name: 'Cancel' }).click();
  await expect(page.getByRole('heading', { name: 'Setup new Sensor', level: 2 })).not.toBeVisible();
});

test('should add and remove a parameter', async ({ page }) => {
  await page.getByRole('button', { name: 'Create Sensor' }).click();
  const dialog = page.getByRole('dialog');

  const firstBlock = dialog.getByTestId('parameter-block-0');
  await expect(firstBlock.getByLabel('Remove Parameter')).toBeDisabled();

  await dialog.getByRole('button', { name: 'Add Parameter' }).click();
  const secondBlock = dialog.getByTestId('parameter-block-1');
  await expect(secondBlock).toBeVisible();
  await expect(firstBlock.getByLabel('Remove Parameter')).toBeEnabled();

  await secondBlock.getByLabel('Parameter Name').fill('Second Parameter');
  await secondBlock.getByLabel('Unit').click();
  await page.getByRole('option', { name: 'Ampere (A)' }).click();
  await secondBlock.getByLabel('Sensor Type').fill('Temperature');
  await page.getByRole('option', { name: 'Temperature' }).click();
  await secondBlock.getByLabel('Alarm Limit From').fill('0');
  await secondBlock.getByLabel('Alarm Limit To').fill('10');

  await secondBlock.getByLabel('Remove Parameter').click();
  await expect(secondBlock).not.toBeVisible();
  await expect(firstBlock.getByLabel('Remove Parameter')).toBeDisabled();
});
