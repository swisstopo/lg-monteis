import { Browser } from '@playwright/test';
import { createExperimentInDialog, openExperimentTable } from '../support/experiment-table';
import { uniqueExperimentName } from '../support/experiments';
import { expect, test } from '../support/fixtures';
import { addUserToGroup, createExperimentAccessGroups } from '../support/keycloak';
import { openAppAs, SEED_USERS, SeedUser } from '../support/login';
import { findExperimentIdByName } from '../support/monteis-api';
import {
  createSensorInDialog,
  filterSensorsByAlias,
  openSensorTable,
  sensorAliasCell,
} from '../support/sensor-table';
import { dataRows } from '../support/table';

/**
 * End-to-end check of per-experiment read access: an admin creates an experiment and a sensor on
 * it, the experiment gets its Keycloak group with bob in it, and only bob sees that sensor.
 *
 * This is the full path the sensor's experiment_sensor row gates - the row JooqSensorRepository
 * writes on create, which the can_access_sensor() row-level-security predicate reads.
 */
test('shows a new sensor only to members of its experiment group', async ({
  page,
  browser,
  adminApi,
  keycloakApi,
}) => {
  // Three full Keycloak logins plus experiment and sensor creation through the UI.
  test.slow();

  await openAppAs(page, SEED_USERS.admin);

  const experimentName = uniqueExperimentName();
  await openExperimentTable(page);
  await createExperimentInDialog(page, experimentName);
  const dasSensorAlias = `SN-ACCESS-${crypto.randomUUID()}`;
  await openSensorTable(page);
  await createSensorInDialog(page, dasSensorAlias, experimentName);

  const experiment = {
    name: experimentName,
    id: await findExperimentIdByName(adminApi, experimentName),
  };
  const accessGroups = await createExperimentAccessGroups(keycloakApi, experiment);
  await addUserToGroup(keycloakApi, SEED_USERS.bob, accessGroups.read);

  // bob is now a member of "Experiment <name>/read", alice is not.
  await expectSensorVisibility(browser, SEED_USERS.bob, dasSensorAlias, true);
  await expectSensorVisibility(browser, SEED_USERS.alice, dasSensorAlias, false);
});

/**
 * Logs in as one user in a fresh browser context - a new Keycloak login, so the access token
 * carries that user's current group memberships - and checks whether the sensor table has a row
 * for `dasSensorAlias`.
 */
async function expectSensorVisibility(
  browser: Browser,
  user: SeedUser,
  dasSensorAlias: string,
  shouldSeeSensor: boolean,
): Promise<void> {
  const context = await browser.newContext();
  try {
    const page = await context.newPage();
    await openAppAs(page, user);
    await openSensorTable(page);

    const rows = dataRows(page);
    // Both users see the sensors of the experiments they were already in, so rows here prove the
    // grid loaded - without that, the filtered-to-nothing assertion below would also pass on a
    // table that never rendered.
    await expect(rows.first()).toBeVisible();

    // waits for the filtered page, an empty grid in the middle of reloading would pass too
    await filterSensorsByAlias(page, dasSensorAlias);

    if (shouldSeeSensor) {
      await expect(sensorAliasCell(page, dasSensorAlias)).toBeVisible();
      await expect(rows).toHaveCount(1);
    } else {
      await expect(rows).toHaveCount(0);
    }
  } finally {
    await context.close();
  }
}
