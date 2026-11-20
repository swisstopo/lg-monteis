import { Request } from '@playwright/test';
import {
  createExperiment,
  randomSuffix,
  SEEDED_EXPERIMENTS,
  uniqueExperimentName,
} from '../support/experiments';
import { expect, test } from '../support/fixtures';
import { gridCell } from '../support/grid';
import { addUserToGroup, createExperimentAccessGroups } from '../support/keycloak';
import { openAppAs, SEED_USERS } from '../support/login';
import {
  createSensorWithoutDasKey,
  openNotificationPage,
  recipientChips,
  recipientsSection,
  viewTab,
} from '../support/monitoring';
import { findExperimentIdByName } from '../support/monteis-api';

// TODO MON-235: menu journeys once the layout calls addMonitoringMenu: a PI sees only their
// experiments, an admin sees all

// TEMP-1 of Alpha, with readings in db/timescale/seed/R__seed_dev_data.sql
const TEMPERATURE = 'monteis-001 – Temperature Param';
const TEMPERATURE_DAS_KEY = 'SOL_EXPERTS__TEMP-1__temperature';

function isMeasurementsPage(request: Request): boolean {
  return new URL(request.url()).pathname === '/api/measurements';
}

test('"Open details" opens a measurement table tab filtered to the sensor parameter', async ({
  page,
  adminApi,
}) => {
  const alphaId = await findExperimentIdByName(adminApi, SEEDED_EXPERIMENTS.alpha);
  await openAppAs(page, SEED_USERS.alice);
  await openNotificationPage(page, alphaId);
  await expect(viewTab(page, `Notifications ${SEEDED_EXPERIMENTS.alpha}`)).toBeVisible();

  const firstPage = page.waitForRequest(isMeasurementsPage);
  await page
    .locator('.ag-row', { hasText: TEMPERATURE })
    .getByRole('button', { name: 'Row actions' })
    .click();
  await page.getByRole('menuitem', { name: 'Open details' }).click();

  // the unfiltered table must never flash up
  const filterModel = new URL((await firstPage).url()).searchParams.get('filterModel') ?? '';
  expect(filterModel).toContain(TEMPERATURE_DAS_KEY);
  await expect(page.getByRole('textbox', { name: 'DAS Key Filter Input' })).toHaveValue(
    TEMPERATURE_DAS_KEY,
  );
  await expect(gridCell(page, TEMPERATURE_DAS_KEY)).toBeVisible();
  await expect(viewTab(page, `Notifications ${SEEDED_EXPERIMENTS.alpha}`)).toBeVisible();
});

test('"Open details" is disabled for a sensor parameter without DAS key', async ({
  page,
  adminApi,
}) => {
  const experiment = await createExperiment(adminApi, uniqueExperimentName());
  const sensorName = `e2e-${randomSuffix()}`;
  await createSensorWithoutDasKey(adminApi, experiment.id, sensorName);
  await openAppAs(page, SEED_USERS.admin);
  await openNotificationPage(page, experiment.id);

  await page
    .locator('.ag-row', { hasText: sensorName })
    .getByRole('button', { name: 'Row actions' })
    .click();

  await expect(page.getByRole('menuitem', { name: 'Open details' })).toBeDisabled();
});

test('a PI replaces the default recipients and the saved list survives a reload', async ({
  page,
  adminApi,
  keycloakApi,
}) => {
  // a fresh experiment per test: every browser project saves recipients in parallel
  const experiment = await createExperiment(adminApi, uniqueExperimentName());
  const accessGroups = await createExperimentAccessGroups(keycloakApi, experiment);
  await addUserToGroup(keycloakApi, SEED_USERS.alice, accessGroups['read + write']);
  await addUserToGroup(keycloakApi, SEED_USERS.bob, accessGroups.read);

  await openAppAs(page, SEED_USERS.alice);
  await openNotificationPage(page, experiment.id);
  await expect(page.getByTestId('empty-state')).toBeVisible();
  // never saved => the PIs
  await expect(recipientChips(page)).toHaveText([/Alice Example/]);

  await recipientsSection(page).getByRole('combobox', { name: 'Add recipient' }).click();
  await page.getByRole('option', { name: 'Bob Example (bob@example.test)' }).click();
  await recipientChips(page)
    .filter({ hasText: 'Alice Example' })
    .getByRole('button', { name: 'Remove recipient' })
    .click();
  await expect(recipientChips(page)).toHaveText([/Bob Example/]);

  const saved = page.waitForResponse(
    (response) =>
      response.request().method() === 'PUT' &&
      new URL(response.url()).pathname === `/api/experiments/${experiment.id}/recipients`,
  );
  await recipientsSection(page).getByRole('button', { name: 'Save recipients' }).click();
  expect((await saved).ok()).toBe(true);

  await page.reload();
  await expect(recipientChips(page)).toHaveText([/Bob Example/]);
});
