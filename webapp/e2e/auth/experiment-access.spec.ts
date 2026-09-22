import { Browser, expect, Page, test } from '@playwright/test';
import { createKeycloakAdminApi, grantExperimentAccess } from '../support/keycloak';
import { loginAsAdmin, loginAsAlice, loginAsBob } from '../support/login';
import { createMonteisApi, findExperimentIdByName } from '../support/monteis-api';

const APP_URL = 'http://localhost:4200/';

// Every run of this test creates its own experiment, named after this test's Greek letter plus a
// random suffix. Another test that needs its own experiments takes the next letter.
const GREEK_LETTER = 'Alpha';

/**
 * End-to-end check of per-experiment read access: an admin creates an experiment and a sensor on
 * it, the experiment gets its Keycloak group with bob in it, and only bob sees that sensor.
 *
 * This is the full path the sensor's experiment_sensor row gates - the row JooqSensorRepository
 * writes on create, which the can_access_sensor() row-level-security predicate reads.
 */
test('shows a new sensor only to members of its experiment group', async ({ page, browser }) => {
  // Three full Keycloak logins plus experiment and sensor creation through the UI.
  test.slow();

  const adminApi = await createMonteisApi('admin-user', 'admin-user');
  const keycloakApi = await createKeycloakAdminApi();

  try {
    await page.goto(APP_URL);
    await loginAsAdmin(page);

    const experimentName = await createExperiment(page);
    const dasSensorAlias = `SN-ACCESS-${crypto.randomUUID()}`;
    await createSensor(page, dasSensorAlias, experimentName);

    await grantExperimentAccess(keycloakApi, {
      experimentName,
      experimentId: await findExperimentIdByName(adminApi, experimentName),
      username: 'bob',
    });

    // bob is now a member of "Experiment <name>", alice is not.
    await expectSensorVisibility(browser, loginAsBob, dasSensorAlias, true);
    await expectSensorVisibility(browser, loginAsAlice, dasSensorAlias, false);
  } finally {
    await adminApi.dispose();
    await keycloakApi.dispose();
  }
});

/**
 * Creates this test's experiment through the UI, as `<Greek letter>-<random>`. Experiment names
 * are unique in the database and these tests never delete what they create, so every run - and
 * every browser project running in parallel - needs its own name.
 */
async function createExperiment(page: Page): Promise<string> {
  await page.getByRole('link', { name: 'Experiment' }).click();
  await page.getByRole('button', { name: 'Create Experiment' }).click();
  await expect(page.getByRole('heading', { name: 'Setup new Experiment', level: 2 })).toBeVisible();

  const dialog = page.getByRole('dialog');
  // Slice unique ID so we don't hit the 50 char max-length validation bounds
  const experimentName = `${GREEK_LETTER}-${crypto.randomUUID().substring(0, 8)}`;

  await dialog.getByLabel('Experiment Name').fill(experimentName);
  // Using dates like 01/01 and 05/05 prevents locale formatting parsing errors in Playwright
  await dialog.getByLabel('Start Date').fill('01/01/2030');
  await dialog.getByLabel('End Date').fill('05/05/2030');
  await dialog.getByRole('button', { name: 'Save', exact: true }).click();

  await expect(page.getByText('Experiment saved successfully.')).toBeVisible();

  return experimentName;
}

async function createSensor(
  page: Page,
  dasSensorAlias: string,
  experimentName: string,
): Promise<void> {
  await page.getByRole('link', { name: 'Sensor' }).click();
  await page.getByRole('button', { name: 'Create Sensor' }).click();
  await expect(page.getByRole('heading', { name: 'Setup new Sensor', level: 2 })).toBeVisible();

  // Several sensor table column headers reuse the same text as the dialog's field labels (e.g.
  // "DAS Sensor Alias", "Unit", "X (Local)"), so the table's filter inputs, now visible behind the
  // dialog, also match the generic page.getByLabel(...) substring match. Scope to the dialog.
  const dialog = page.getByRole('dialog');

  await dialog.getByLabel('DAS Sensor Alias').fill(dasSensorAlias);
  await dialog.getByLabel('Sensor Name').fill('E2E ACCESS TEST');

  // 'DAS' alone substring-matches 'DAS Sensor Alias' and 'DAS Parameter Alias' too - scope exactly.
  await dialog.getByLabel('DAS', { exact: true }).click();
  await page.getByRole('option', { name: 'SolExperts' }).click();

  // The CDK overlay is position: fixed and can render outside the actual viewport (WebKit gives
  // it a zero-overlap bounding box), so no click - mouse or forced - has coordinates to land on.
  // Typing the exact name narrows the autocomplete to this one option and Enter selects it via the
  // keyboard instead, which is independent of the overlay's on-screen position. Selecting the
  // option is what binds the experiment: the form only sends mainExperimentId for a picked option.
  const experimentInput = dialog.getByRole('combobox', { name: 'Main Experiment' });
  await experimentInput.fill(experimentName);
  await expect(page.getByRole('option', { name: experimentName, exact: true })).toBeVisible();
  await experimentInput.press('ArrowDown');
  await experimentInput.press('Enter');

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
}

/**
 * Logs in as one user in a fresh browser context - a new Keycloak login, so the access token
 * carries that user's current group memberships - and checks whether the sensor table has a row
 * for `dasSensorAlias`.
 */
async function expectSensorVisibility(
  browser: Browser,
  loginAs: (page: Page) => Promise<void>,
  dasSensorAlias: string,
  shouldSeeSensor: boolean,
): Promise<void> {
  const context = await browser.newContext();
  try {
    const page = await context.newPage();
    await page.goto(APP_URL);
    await loginAs(page);
    await page.getByRole('link', { name: 'Sensor' }).click();

    // One selection checkbox per data row - the header and floating-filter rows carry gridcells
    // too, so counting those would never reach zero.
    const rows = page.getByRole('checkbox', { name: /toggle row selection/ });
    // Both users see the sensors of the experiments they were already in, so rows here prove the
    // grid loaded - without that, the filtered-to-nothing assertion below would also pass on a
    // table that never rendered.
    await expect(rows.first()).toBeVisible();

    await page.getByRole('textbox', { name: 'DAS Sensor Alias Filter Input' }).fill(dasSensorAlias);

    if (shouldSeeSensor) {
      // Exact: the floating-filter cell now holds the same text and would match a substring.
      await expect(page.getByRole('gridcell', { name: dasSensorAlias, exact: true })).toBeVisible();
      await expect(rows).toHaveCount(1);
    } else {
      await expect(rows).toHaveCount(0);
    }
  } finally {
    await context.close();
  }
}
