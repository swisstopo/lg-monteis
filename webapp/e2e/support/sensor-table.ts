import { expect, Page } from '@playwright/test';
import { waitForAutofocus } from './dialog';
import { hasPath } from './responses';

/**
 * Creates sensor `dasSensorAlias` through the Create Sensor dialog of the open sensor table,
 * which needs an admin, optionally on `mainExperimentName`. Tests that change a sensor change
 * their own: a seeded one is shared with every other test.
 */
export async function createSensorInDialog(
  page: Page,
  dasSensorAlias: string,
  mainExperimentName?: string,
): Promise<void> {
  await page.getByRole('button', { name: 'Create Sensor' }).click();
  await expect(page.getByRole('heading', { name: 'Setup new Sensor', level: 2 })).toBeVisible();

  // Several sensor table column headers reuse the same text as the dialog's field labels (e.g.
  // "DAS Sensor Alias", "Unit", "X (Local)"), so the table's filter inputs, now visible behind the
  // dialog, also match the generic page.getByLabel(...) substring match. Scope to the dialog.
  const dialog = page.getByRole('dialog');
  await waitForAutofocus(dialog);

  await dialog.getByLabel('DAS Sensor Alias').fill(dasSensorAlias);
  await dialog.getByLabel('Sensor Name').fill('E2E TEST');

  // 'DAS' alone substring-matches 'DAS Sensor Alias' and 'DAS Parameter Alias' too - scope exactly.
  await dialog.getByLabel('DAS', { exact: true }).click();
  await page.getByRole('option', { name: 'SolExperts' }).click();

  if (mainExperimentName) {
    // The CDK overlay is position: fixed and can render outside the actual viewport (WebKit gives
    // it a zero-overlap bounding box), so no click - mouse or forced - has coordinates to land on.
    // Typing the exact name narrows the autocomplete to this one option and Enter selects it via
    // the keyboard instead. Selecting the option is what binds the experiment: the form only
    // sends mainExperimentId for a picked option.
    const experimentInput = dialog.getByRole('combobox', { name: 'Main Experiment' });
    await experimentInput.fill(mainExperimentName);
    await expect(page.getByRole('option', { name: mainExperimentName, exact: true })).toBeVisible();
    await experimentInput.press('ArrowDown');
    await experimentInput.press('Enter');
  }

  const firstParameter = dialog.getByTestId('parameter-block-0');
  await firstParameter.getByLabel('Parameter Name').fill('Temperature Reading');
  await firstParameter.getByLabel('Unit').click();
  await page.getByRole('option', { name: 'Ampere (A)' }).click();
  await firstParameter.getByLabel('Sensor Type').fill('Temperature');
  await page.getByRole('option', { name: 'Temperature' }).click();
  await firstParameter.getByLabel('Alarm Limit From').fill('10');
  await firstParameter.getByLabel('Alarm Limit To').fill('100');

  await dialog.getByLabel('X (Local)').fill('100');
  await dialog.getByLabel('Y (Local)').fill('200');
  await dialog.getByLabel('Z (Local)').fill('300');

  await dialog.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(page.getByText('Sensor saved successfully.')).toBeVisible();
  await expect(dialog).toHaveCount(0);
}

/**
 * Filters the sensor table down to `dasSensorAlias` and selects its row. Waits for the filtered
 * page first: a click while the grid still reloads lands on a row that is about to be replaced.
 */
export async function selectSensor(page: Page, dasSensorAlias: string): Promise<void> {
  const filtered = page.waitForResponse(
    (response) =>
      hasPath(response, '/api/sensors') &&
      (new URL(response.url()).searchParams.get('filterModel') ?? '').includes(dasSensorAlias),
  );
  await page.getByRole('textbox', { name: 'DAS Sensor Alias Filter Input' }).fill(dasSensorAlias);
  expect((await filtered).ok()).toBe(true);
  // Exact: the floating-filter cell holds the same text and would match a substring.
  await page.getByRole('gridcell', { name: dasSensorAlias, exact: true }).click();
}
