import { expect, test } from '@playwright/test';
import { pickAutocompleteOption, waitForAutofocus } from '../support/dialog';
import { openAppAs, SEED_USERS } from '../support/login';
import {
  createSensorInDialog,
  fillLocalCoordinates,
  fillRequiredSensorFields,
  firstParameter,
  formulaInput,
  openCreateSensorDialog,
  openSensorTable,
  selectSensor,
} from '../support/sensor-table';

// Coordinates FulcrumStubConfiguration (core/src/test) reports for every record it is asked for.
// Keep both in step.
const STUB_FULCRUM_COORDINATES = { x: 2579321, y: 1247865, z: 512 };

// The one record id that same stub reports as missing (UNKNOWN_RECORD_ID over there). A
// well-formed UUID, so it passes the form's own validation and only fails once Fulcrum is asked.
const UNKNOWN_FULCRUM_RECORD_ID = '00000000-0000-4000-8000-000000000000';

// (das, das_sensor_alias) is unique and the browser projects run in parallel, every alias is too
function uniqueAlias(prefix: string): string {
  return `${prefix}-${crypto.randomUUID()}`;
}

test.beforeEach(async ({ page }) => {
  await openAppAs(page, SEED_USERS.admin);
  await openSensorTable(page);
});

test('should create sensor', async ({ page }) => {
  const dialog = await openCreateSensorDialog(page);
  await fillRequiredSensorFields(dialog, { dasSensorAlias: uniqueAlias('SN-TEMP') });
  await fillLocalCoordinates(dialog);
  await pickAutocompleteOption(formulaInput(firstParameter(dialog)), 'x * 1000', 'x * 1000 (v1)');

  await dialog.getByRole('button', { name: 'Save', exact: true }).click();

  await expect(page.getByText('Sensor saved successfully.')).toBeVisible();
});

test('should create sensor with a fulcrum id and take its coordinates from fulcrum', async ({
  page,
}) => {
  const fulcrumId = crypto.randomUUID();
  const dialog = await openCreateSensorDialog(page);
  await fillRequiredSensorFields(dialog, {
    dasSensorAlias: uniqueAlias('SN-FULCRUM'),
    sensorName: 'E2E FULCRUM TEST',
  });
  // the stub answers any record id with a row carrying that same id
  await dialog.getByLabel('Fulcrum ID').fill(fulcrumId);
  await expect(dialog.getByLabel('X (Local)')).toBeDisabled();

  // the response of the create call proves the backend really asked Fulcrum, createSensor
  // overwrites the form's coordinates with the record's. it is read on the playwright side,
  // chromium drops the body of a browser response once the grid reloads
  const created = new Promise<{ fulcrumId: string; coordinates: unknown }>((resolve) =>
    page.route('**/api/sensors', async (route) => {
      if (route.request().method() !== 'POST') return route.fallback();
      const response = await route.fetch();
      resolve(await response.json());
      await route.fulfill({ response });
    }),
  );

  await dialog.getByRole('button', { name: 'Save', exact: true }).click();

  await expect(page.getByText('Sensor saved successfully.')).toBeVisible();
  const createdSensor = await created;
  expect(createdSensor.fulcrumId).toBe(fulcrumId);
  expect(createdSensor.coordinates).toEqual(STUB_FULCRUM_COORDINATES);
});

test('should reject a fulcrum id that is not a uuid', async ({ page }) => {
  const dialog = await openCreateSensorDialog(page);

  await dialog.getByLabel('Fulcrum ID').fill('not-a-uuid');
  await dialog.getByLabel('Fulcrum ID').blur();

  await expect(page.getByText('Fulcrum ID must be a UUID')).toBeVisible();
});

test('should refuse to save a fulcrum id that no fulcrum record matches', async ({ page }) => {
  const dialog = await openCreateSensorDialog(page);
  await fillRequiredSensorFields(dialog, {
    dasSensorAlias: uniqueAlias('SN-MISSING'),
    sensorName: 'E2E FULCRUM MISSING',
  });
  // well-formed, only the backend can tell that Fulcrum holds no such record
  await dialog.getByLabel('Fulcrum ID').fill(UNKNOWN_FULCRUM_RECORD_ID);
  await expect(page.getByText('Fulcrum ID must be a UUID')).toHaveCount(0);

  await dialog.getByRole('button', { name: 'Save', exact: true }).click();

  // fulcrum.sensor.not-found carries no field, so it is a toast, not an error on the input
  await expect(
    page.getByText(
      'Fulcrum holds no record with this Fulcrum ID. Check the ID in Fulcrum, or clear it and enter the coordinates manually.',
    ),
  ).toBeVisible();
  await expect(page.getByText('Sensor saved successfully.')).toHaveCount(0);
  // nothing was saved, so the dialog stays open with the entered values intact
  await expect(page.getByRole('heading', { name: 'Setup new Sensor', level: 2 })).toBeVisible();
  await expect(dialog.getByLabel('Fulcrum ID')).toHaveValue(UNKNOWN_FULCRUM_RECORD_ID);
});

test('should disable the coordinates while a fulcrum id is entered', async ({ page }) => {
  const dialog = await openCreateSensorDialog(page);
  const coordinates = ['X (Local)', 'Y (Local)', 'Z (Local)'].map((label) =>
    dialog.getByLabel(label),
  );

  // without a Fulcrum record the coordinates are the sensor's only source for them
  for (const coordinate of coordinates) await expect(coordinate).toBeEnabled();

  await dialog.getByLabel('Fulcrum ID').fill(crypto.randomUUID());
  for (const coordinate of coordinates) await expect(coordinate).toBeDisabled();

  // clearing the Fulcrum ID hands the coordinates back to the user
  await dialog.getByLabel('Fulcrum ID').fill('');
  for (const coordinate of coordinates) await expect(coordinate).toBeEnabled();
});

test('should update sensor', async ({ page }) => {
  const dasSensorAlias = uniqueAlias('SN-UPDATE');
  await createSensorInDialog(page, dasSensorAlias);
  await selectSensor(page, dasSensorAlias);
  await page.getByRole('button', { name: 'Edit Sensor' }).click();
  await expect(page.getByRole('heading', { name: 'Edit Sensor', level: 2 })).toBeVisible();
  const dialog = page.getByRole('dialog');
  await waitForAutofocus(dialog);

  const updatedAlias = uniqueAlias('SN-UPDATED');
  await dialog.getByLabel('DAS Sensor Alias').fill(updatedAlias);
  await dialog.getByLabel('Sensor Name').fill('E2E TEST UPDATED');
  const parameter = firstParameter(dialog);
  await parameter.getByLabel('Alarm Limit From').fill('20');
  await parameter.getByLabel('Alarm Limit To').fill('200');
  await pickAutocompleteOption(formulaInput(parameter), 'x * 1000', 'x * 1000 (v1)');

  await dialog.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(dialog).toHaveCount(0);

  await selectSensor(page, updatedAlias);
});

test('should fail to create existing sensor', async ({ page }) => {
  const dasSensorAlias = uniqueAlias('SN-TEMP');
  const dialog = await openCreateSensorDialog(page);
  await fillRequiredSensorFields(dialog, { dasSensorAlias });
  await fillLocalCoordinates(dialog);
  await dialog.getByRole('button', { name: 'Save and create new', exact: true }).click();
  await expect(page.getByText('Sensor saved successfully.')).toBeVisible();

  // the dialog is open again for the next sensor, with the same alias
  await fillRequiredSensorFields(dialog, { dasSensorAlias, sensorName: 'E2E TEST 2' });
  await fillLocalCoordinates(dialog, { x: '0', y: '10', z: '20' });
  await dialog.getByRole('button', { name: 'Save', exact: true }).click();

  await expect(page.getByText('An entity with the same code already exists')).toBeVisible();
});

test('should show required validation errors', async ({ page }) => {
  const dialog = await openCreateSensorDialog(page);

  await dialog.getByLabel('DAS Sensor Alias').fill('x');
  await dialog.getByLabel('DAS Sensor Alias').fill('');
  await dialog.getByLabel('DAS Sensor Alias').blur();
  await expect(page.getByText('DAS sensor alias is required')).toBeVisible();

  const parameter = firstParameter(dialog);
  await parameter.getByLabel('Parameter Name').fill('x');
  await parameter.getByLabel('Parameter Name').fill('');
  await parameter.getByLabel('Parameter Name').blur();
  await expect(page.getByText('Parameter name is required')).toBeVisible();

  await expect(dialog.getByRole('button', { name: 'Save', exact: true })).toBeDisabled();
});

test('should reject invalid alarm limits', async ({ page }) => {
  const dialog = await openCreateSensorDialog(page);
  const parameter = firstParameter(dialog);

  await parameter.getByLabel('Alarm Limit From').fill('100');
  await parameter.getByLabel('Alarm Limit To').fill('10');
  await parameter.getByLabel('Alarm Limit To').blur();

  await expect(page.getByText('This value must be higher than the lower limit')).toBeVisible();
});

test('should close dialog on cancel', async ({ page }) => {
  const dialog = await openCreateSensorDialog(page);

  await dialog.getByRole('button', { name: 'Cancel' }).click();

  await expect(page.getByRole('heading', { name: 'Setup new Sensor', level: 2 })).not.toBeVisible();
});

test('should add and remove a parameter', async ({ page }) => {
  const dialog = await openCreateSensorDialog(page);
  const firstBlock = firstParameter(dialog);
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
