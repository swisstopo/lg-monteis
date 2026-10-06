import {
  editExperimentButton,
  openExperimentTable,
  selectExperiment,
  viewExperimentButton,
} from '../support/experiment-table';
import { createExperiment, SEEDED_EXPERIMENTS, uniqueExperimentName } from '../support/experiments';
import { expect, test } from '../support/fixtures';
import { addUserToGroup, createExperimentAccessGroups } from '../support/keycloak';
import { label, openAppAs, SEED_USERS, SeedUser } from '../support/login';
import { expectTableToolbar } from '../support/table';

/**
 * The SPA's cosmetic gating per privilege level (seed users of docker/keycloak/realm/patch.local.json).
 * The backend enforces every rule; these tests only prove the UI follows `/api/me`:
 * - every level sees the Sensor menu entry and the sensor table (row-level security filters the
 *   sensors); only admins see the sensor write actions;
 * - Create Experiment is admin-only;
 * - View is shown to everyone and opens the selected experiment read-only; Edit Experiment is
 *   shown in addition to callers with any experiment write access and enabled only on rows they
 *   may write.
 */

const WITHOUT_WRITE_ACCESS: SeedUser[] = [SEED_USERS.bob, SEED_USERS.basisUser];
const NON_ADMINS: SeedUser[] = [SEED_USERS.alice, ...WITHOUT_WRITE_ACCESS];

test.describe(label(SEED_USERS.admin), () => {
  test('sees the Sensor menu, the sensor write actions and Create Experiment', async ({ page }) => {
    await openAppAs(page, SEED_USERS.admin);

    await page.getByRole('link', { name: 'Sensor' }).click();
    await expect(page.getByRole('button', { name: 'Create Sensor' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Edit Sensor' })).toBeVisible();

    await openExperimentTable(page);
    await expect(page.getByRole('button', { name: 'Create Experiment' })).toBeVisible();
    await selectExperiment(page, SEEDED_EXPERIMENTS.alpha);
    await expect(editExperimentButton(page)).toBeEnabled();
  });
});

for (const user of NON_ADMINS) {
  test.describe(label(user), () => {
    test('sees the sensor table without the sensor write actions', async ({ page }) => {
      await openAppAs(page, user);

      await page.getByRole('link', { name: 'Sensor' }).click();
      await expectTableToolbar(page);
      await expect(page.getByRole('button', { name: 'Create Sensor' })).toHaveCount(0);
      await expect(page.getByRole('button', { name: 'Edit Sensor' })).toHaveCount(0);
    });

    test('does not see Create Experiment', async ({ page }) => {
      await openAppAs(page, user);
      await openExperimentTable(page);

      await expectTableToolbar(page);
      await expect(page.getByRole('button', { name: 'Create Experiment' })).toHaveCount(0);
    });
  });
}

test(`${label(SEED_USERS.alice)} may edit only the experiments with write access`, async ({
  page,
  adminApi,
  keycloakApi,
}) => {
  // A Keycloak admin session, an experiment and its access groups, then a full login.
  test.slow();
  const readOnlyExperiment = await createExperiment(adminApi, uniqueExperimentName());
  const accessGroups = await createExperimentAccessGroups(keycloakApi, readOnlyExperiment);
  await addUserToGroup(keycloakApi, SEED_USERS.alice, accessGroups.read);

  await openAppAs(page, SEED_USERS.alice);
  await openExperimentTable(page);

  await selectExperiment(page, SEEDED_EXPERIMENTS.alpha);
  await expect(editExperimentButton(page)).toBeEnabled();
  await expect(viewExperimentButton(page)).toBeEnabled();

  await selectExperiment(page, readOnlyExperiment.name);
  await expect(editExperimentButton(page)).toBeDisabled();
  await expect(viewExperimentButton(page)).toBeEnabled();
});

for (const user of WITHOUT_WRITE_ACCESS) {
  test(`${label(user)} gets no Edit Experiment button`, async ({ page }) => {
    await openAppAs(page, user);
    await openExperimentTable(page);

    await expectTableToolbar(page);
    await expect(editExperimentButton(page)).toHaveCount(0);
  });
}

test(`${label(SEED_USERS.bob)} views a readable experiment read-only`, async ({ page }) => {
  await openAppAs(page, SEED_USERS.bob);
  await openExperimentTable(page);

  await expect(viewExperimentButton(page)).toBeDisabled();
  await selectExperiment(page, SEEDED_EXPERIMENTS.beta);
  await viewExperimentButton(page).click();

  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('heading', { name: 'View Experiment', level: 2 })).toBeVisible();
  await expect(dialog.getByLabel('Experiment Name')).toHaveValue(SEEDED_EXPERIMENTS.beta);
  await expect(dialog.getByLabel('Experiment Name')).toBeDisabled();
  await expect(dialog.getByLabel('Comment')).toBeDisabled();
  await expect(dialog.getByLabel('Start Date')).toBeDisabled();
  await expect(dialog.getByRole('button', { name: 'Save', exact: true })).toHaveCount(0);
  await expect(dialog.getByRole('button', { name: 'Add Document' })).toHaveCount(0);

  await dialog.getByRole('button', { name: 'Close' }).click();
  await expect(dialog).toHaveCount(0);
});
