import { expect, Locator, Page } from '@playwright/test';
import { pickAutocompleteOption, waitForAutofocus } from './dialog';
import { expectGridPage, filterGrid, gridCell } from './grid';

const SENSORS = '/api/sensors';

/** Opens the sensor table and waits until the backend answered its first page. */
export async function openSensorTable(page: Page): Promise<void> {
  const firstPage = expectGridPage(page, SENSORS);
  await page.getByRole('link', { name: 'Sensor' }).click();
  await firstPage;
}

/** Filters the table by DAS sensor alias and waits for the filtered page. */
export async function filterSensorsByAlias(page: Page, dasSensorAlias: string): Promise<void> {
  await filterGrid(page, SENSORS, 'DAS Sensor Alias Filter Input', dasSensorAlias);
}

export function sensorAliasCell(page: Page, dasSensorAlias: string): Locator {
  return gridCell(page, dasSensorAlias);
}

/** Filters the table down to `dasSensorAlias` and selects its row. */
export async function selectSensor(page: Page, dasSensorAlias: string): Promise<void> {
  await filterSensorsByAlias(page, dasSensorAlias);
  await sensorAliasCell(page, dasSensorAlias).click();
}

/**
 * Opens the Create Sensor dialog, ready to be filled. Locate its fields through the returned
 * dialog: several column headers of the table behind it ("DAS Sensor Alias", "Unit", "X (Local)")
 * reuse its labels, and their filter inputs match a page-wide getByLabel as well.
 */
export async function openCreateSensorDialog(page: Page): Promise<Locator> {
  await page.getByRole('button', { name: 'Create Sensor' }).click();
  await expect(page.getByRole('heading', { name: 'Setup new Sensor', level: 2 })).toBeVisible();
  const dialog = page.getByRole('dialog');
  await waitForAutofocus(dialog);
  return dialog;
}

/** The fields of the first parameter of the open sensor dialog. */
export function firstParameter(dialog: Locator): Locator {
  return dialog.getByTestId('parameter-block-0');
}

/**
 * The formula input of `parameter`. getByLabel would also match its results listbox, Material's
 * autocomplete gives both the same aria-labelledby; the combobox role is the input's alone.
 */
export function formulaInput(parameter: Locator): Locator {
  return parameter.getByRole('combobox', {
    name: "Formula Expression (Optional, defaults to 'x')",
  });
}

/** Fills what every sensor needs: alias, name, DAS and its first parameter. */
export async function fillRequiredSensorFields(
  dialog: Locator,
  { dasSensorAlias, sensorName = 'E2E TEST' }: { dasSensorAlias: string; sensorName?: string },
): Promise<void> {
  const page = dialog.page();
  await dialog.getByLabel('DAS Sensor Alias').fill(dasSensorAlias);
  await dialog.getByLabel('Sensor Name').fill(sensorName);

  // exact: 'DAS' alone also matches 'DAS Sensor Alias' and 'DAS Parameter Alias'
  await dialog.getByLabel('DAS', { exact: true }).click();
  await page.getByRole('option', { name: 'SolExperts' }).click();

  const parameter = firstParameter(dialog);
  await parameter.getByLabel('Parameter Name').fill('Temperature Reading');
  await parameter.getByLabel('Unit').click();
  await page.getByRole('option', { name: 'Ampere (A)' }).click();
  await parameter.getByLabel('Sensor Type').fill('Temperature');
  await page.getByRole('option', { name: 'Temperature' }).click();
  await parameter.getByLabel('Alarm Limit From').fill('10');
  await parameter.getByLabel('Alarm Limit To').fill('100');
}

/** Fills the local coordinates a sensor without Fulcrum ID needs. */
export async function fillLocalCoordinates(
  dialog: Locator,
  { x, y, z }: { x: string; y: string; z: string } = { x: '100', y: '200', z: '300' },
): Promise<void> {
  await dialog.getByLabel('X (Local)').fill(x);
  await dialog.getByLabel('Y (Local)').fill(y);
  await dialog.getByLabel('Z (Local)').fill(z);
}

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
  const dialog = await openCreateSensorDialog(page);
  await fillRequiredSensorFields(dialog, { dasSensorAlias });
  if (mainExperimentName) {
    // picking the option binds the experiment, the form only sends mainExperimentId for one
    await pickAutocompleteOption(
      dialog.getByRole('combobox', { name: 'Main Experiment' }),
      mainExperimentName,
    );
  }
  await fillLocalCoordinates(dialog);
  await dialog.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(dialog).toHaveCount(0);
}
