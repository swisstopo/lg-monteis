import { expect, Page, test } from '@playwright/test';
import { createKeycloakAdminApi, grantExperimentAccess } from '../support/keycloak';
import {
  loginAsAdmin,
  loginAsAlice,
  loginAsBasisUser,
  loginAsBob,
  loginAsGlobalEditor,
} from '../support/login';
import { createMonteisApi, findExperimentIdByName } from '../support/monteis-api';

/**
 * The SPA's cosmetic gating per privilege level (seed users of docker/keycloak/realm/patch.local.json).
 * The backend enforces every rule; these tests only prove the UI follows `/api/me`:
 * - every level sees the Sensor menu entry and the sensor table (row-level security filters the
 *   sensors); only admins see the sensor write actions;
 * - Create Experiment is admin-only;
 * - Edit Experiment is shown to callers with any experiment write access and enabled only on rows
 *   they may write.
 */

const APP_URL = 'http://localhost:4200/';
// Seeded experiment 00000000-0000-7000-8000-000000000301; alice holds "read + write" on it.
const ALPHA_EXPERIMENT = 'Mont Terri Alpha';
// Every run that needs its own experiment names it after its Greek letter plus a random suffix;
// experiment-access.spec.ts takes Alpha, this file the next letter.
const GREEK_LETTER = 'Beta';

type LoginAs = (page: Page) => Promise<void>;

// Levels without any experiment write access.
const WITHOUT_WRITE_ACCESS: { level: string; loginAs: LoginAs }[] = [
  { level: 'ExperimentUser (bob)', loginAs: loginAsBob },
  { level: 'Basisrolle (basis-user)', loginAs: loginAsBasisUser },
];

const NON_ADMINS: { level: string; loginAs: LoginAs }[] = [
  { level: 'global editor (editor-user)', loginAs: loginAsGlobalEditor },
  { level: 'ExperimentPI (alice)', loginAs: loginAsAlice },
  ...WITHOUT_WRITE_ACCESS,
];

test.describe('admin (admin-user)', () => {
  test('sees the Sensor menu, the sensor write actions and Create Experiment', async ({ page }) => {
    await openAppAs(page, loginAsAdmin);

    await page.getByRole('link', { name: 'Sensor' }).click();
    await expect(page.getByRole('button', { name: 'Create Sensor' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Edit Sensor' })).toBeVisible();

    await page.getByRole('link', { name: 'Experiment' }).click();
    await expect(page.getByRole('button', { name: 'Create Experiment' })).toBeVisible();
    await selectExperimentRow(page, ALPHA_EXPERIMENT);
    await expect(editExperimentButton(page)).toBeEnabled();
  });
});

for (const { level, loginAs } of NON_ADMINS) {
  test.describe(level, () => {
    test('sees the sensor table without the sensor write actions', async ({ page }) => {
      await openAppAs(page, loginAs);

      await page.getByRole('link', { name: 'Sensor' }).click();
      // Download is always rendered, so the table header is there.
      await expect(page.getByRole('button', { name: 'Download' })).toBeVisible();
      await expect(page.getByRole('button', { name: 'Create Sensor' })).toHaveCount(0);
      await expect(page.getByRole('button', { name: 'Edit Sensor' })).toHaveCount(0);
    });

    test('does not see Create Experiment', async ({ page }) => {
      await openAppAs(page, loginAs);
      await page.getByRole('link', { name: 'Experiment' }).click();

      // Download is always rendered, so the table header is there.
      await expect(page.getByRole('button', { name: 'Download' })).toBeVisible();
      await expect(page.getByRole('button', { name: 'Create Experiment' })).toHaveCount(0);
    });
  });
}

test('global editor may edit any experiment row', async ({ page }) => {
  await openAppAs(page, loginAsGlobalEditor);
  await page.getByRole('link', { name: 'Experiment' }).click();

  await selectExperimentRow(page, ALPHA_EXPERIMENT);

  await expect(editExperimentButton(page)).toBeEnabled();
});

test('ExperimentPI may edit only the experiments with write access', async ({ page }) => {
  // A Keycloak admin session, an experiment and its access groups, then a full login.
  test.slow();
  const readOnlyExperiment = await createExperimentReadableBy('alice');

  await openAppAs(page, loginAsAlice);
  await page.getByRole('link', { name: 'Experiment' }).click();

  await selectExperimentRow(page, ALPHA_EXPERIMENT);
  await expect(editExperimentButton(page)).toBeEnabled();

  await selectExperimentRow(page, readOnlyExperiment);
  await expect(editExperimentButton(page)).toBeDisabled();
});

for (const { level, loginAs } of WITHOUT_WRITE_ACCESS) {
  test(`${level} gets no Edit Experiment button`, async ({ page }) => {
    await openAppAs(page, loginAs);
    await page.getByRole('link', { name: 'Experiment' }).click();

    await expect(page.getByRole('button', { name: 'Download' })).toBeVisible();
    await expect(editExperimentButton(page)).toHaveCount(0);
  });
}

/**
 * Opens the app, logs in, and waits for the `/api/me` answer, so assertions that something is
 * absent cannot pass on the not-yet-loaded (fail-closed) state.
 */
async function openAppAs(page: Page, loginAs: LoginAs): Promise<void> {
  const currentUser = page.waitForResponse(
    (response) => new URL(response.url()).pathname === '/api/me',
  );
  await page.goto(APP_URL);
  await loginAs(page);
  expect((await currentUser).ok()).toBe(true);
}

function editExperimentButton(page: Page) {
  return page.getByRole('button', { name: 'Edit Experiment' });
}

/** Narrows the experiment grid to `experimentName` with the name filter and selects that row. */
async function selectExperimentRow(page: Page, experimentName: string): Promise<void> {
  await page.getByRole('textbox', { name: 'Experiment Name Filter Input' }).fill(experimentName);
  // Exact: the floating-filter cell holds the same text and would match a substring.
  const nameCell = page.getByRole('gridcell', { name: experimentName, exact: true });
  await expect(nameCell).toBeVisible();
  await nameCell.click();
}

/**
 * Creates an experiment through the API as admin-user and gives `username` read-only access to it
 * through its Keycloak `read` subgroup. Returns the experiment's name.
 */
async function createExperimentReadableBy(username: string): Promise<string> {
  const adminApi = await createMonteisApi('admin-user', 'admin-user');
  const keycloakApi = await createKeycloakAdminApi();
  try {
    // Sliced so the name stays below the 50 character limit.
    const experimentName = `${GREEK_LETTER}-${crypto.randomUUID().substring(0, 8)}`;
    const response = await adminApi.post('/api/experiments', {
      data: { name: experimentName, period: { start: '2030-01-01', end: '2030-05-05' } },
    });
    if (!response.ok()) {
      throw new Error(
        `Creating experiment "${experimentName}" failed: ${response.status()} ${await response.text()}`,
      );
    }

    await grantExperimentAccess(keycloakApi, {
      experimentName,
      experimentId: await findExperimentIdByName(adminApi, experimentName),
      username,
      access: 'read',
    });
    return experimentName;
  } finally {
    await adminApi.dispose();
    await keycloakApi.dispose();
  }
}
