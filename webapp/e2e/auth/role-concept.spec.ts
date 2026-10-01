import {
  editExperimentButton,
  experimentNameCell,
  filterExperimentsByName,
  openExperimentTable,
  selectExperiment,
} from '../support/experiment-table';
import {
  createExperiment,
  findExperiment,
  randomSuffix,
  SEEDED_EXPERIMENTS,
  uniqueExperimentName,
} from '../support/experiments';
import { expect, test } from '../support/fixtures';
import { addUserToGroup, createExperimentAccessGroups } from '../support/keycloak';
import { label, openAppAs, SEED_USERS, SeedUser } from '../support/login';
import { hasPath } from '../support/responses';
import { dataRows } from '../support/table';

/**
 * What each privilege level of the MON-196 role concept really gets from the backend, seen
 * through the UI: which experiments the table lists (row-level security), that a basic user can
 * open the 3D view, and that an ExperimentPI can save an edit on its own experiment.
 *
 * Seed data (db/meta/seed, docker/keycloak/realm/patch.local.json): alice holds "read + write" on
 * Mont Terri Alpha, bob holds "read" on Mont Terri Beta, basis-user holds nothing; the admin sees
 * every experiment.
 */

const { alpha: ALPHA, beta: BETA } = SEEDED_EXPERIMENTS;

const VISIBILITY: { user: SeedUser; visible: string[] }[] = [
  { user: SEED_USERS.admin, visible: [ALPHA, BETA] },
  { user: SEED_USERS.alice, visible: [ALPHA] },
  { user: SEED_USERS.bob, visible: [BETA] },
  { user: SEED_USERS.basisUser, visible: [] },
];

for (const { user, visible } of VISIBILITY) {
  test(`${label(user)} sees ${listOrNone(visible)} of the seeded experiments`, async ({ page }) => {
    await openAppAs(page, user);
    await openExperimentTable(page);

    for (const experiment of [ALPHA, BETA]) {
      await filterExperimentsByName(page, experiment);
      await expect(experimentNameCell(page, experiment)).toHaveCount(
        visible.includes(experiment) ? 1 : 0,
      );
    }
  });
}

test(`${label(SEED_USERS.basisUser)} sees no experiment at all`, async ({ page }) => {
  await openAppAs(page, SEED_USERS.basisUser);

  await openExperimentTable(page);

  await expect(dataRows(page)).toHaveCount(0);
});

test(`${label(SEED_USERS.basisUser)} can open the 3D view`, async ({ page }) => {
  await openAppAs(page, SEED_USERS.basisUser);
  await page.getByTitle('Measurements').click();

  // The view loads the sensors it places in the scene; row-level security leaves a basic user
  // none, but the call must succeed - a 403 here used to replace the whole view with an error.
  const sensors = page.waitForResponse((response) => hasPath(response, '/api/sensors'));
  await page.getByRole('link', { name: '3D View' }).click();

  expect((await sensors).status()).toBe(200);
  // app-giro3d is only rendered when the sensor call did not fail
  await expect(page.locator('app-giro3d')).toBeAttached();
});

test(`${label(SEED_USERS.alice)} can save an edit on an experiment with write access`, async ({
  page,
  adminApi,
  keycloakApi,
}) => {
  // A Keycloak admin session, an experiment and its access groups, then a full login.
  test.slow();
  const experiment = await createExperiment(adminApi, uniqueExperimentName());
  const accessGroups = await createExperimentAccessGroups(keycloakApi, experiment);
  await addUserToGroup(keycloakApi, SEED_USERS.alice, accessGroups['read + write']);
  const comment = `Edited by alice ${randomSuffix()}`;

  await openAppAs(page, SEED_USERS.alice);
  await openExperimentTable(page);
  await selectExperiment(page, experiment.name);
  await editExperimentButton(page).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Comment').fill(comment);
  await dialog.getByRole('button', { name: 'Save', exact: true }).click();

  // the update passes the filter chain and the experiments_update row-level security check
  await expect(page.getByText('Experiment saved successfully.')).toBeVisible();
  expect((await findExperiment(adminApi, experiment.id)).comment).toBe(comment);
});

function listOrNone(names: string[]): string {
  return names.length > 0 ? names.join(' and ') : 'none';
}
