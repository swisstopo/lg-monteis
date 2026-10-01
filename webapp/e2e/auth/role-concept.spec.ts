import { expect, Page, test } from '@playwright/test';
import { createExperimentWithAccess } from '../support/experiment-access';
import {
  loginAsAdmin,
  loginAsAlice,
  loginAsBasisUser,
  loginAsBob,
  openAppAs,
} from '../support/login';
import { createMonteisApi } from '../support/monteis-api';

/**
 * What each privilege level of the MON-196 role concept really gets from the backend, seen
 * through the UI: which experiments the table lists (row-level security), that a basic user can
 * open the 3D view, and that an ExperimentPI can save an edit on its own experiment.
 *
 * Seed data (db/meta/seed, docker/keycloak/realm/patch.local.json): alice holds "read + write" on
 * Mont Terri Alpha, bob holds "read" on Mont Terri Beta, basis-user holds nothing; the admin sees
 * every experiment.
 */

// experiment-access.spec.ts takes Alpha, permissions.spec.ts Beta.
const GREEK_LETTER = 'Gamma';

const ALPHA = 'Mont Terri Alpha';
const BETA = 'Mont Terri Beta';

type LoginAs = (page: Page) => Promise<void>;

const VISIBILITY: { level: string; loginAs: LoginAs; alpha: boolean; beta: boolean }[] = [
  { level: 'MonteisAdmin (admin-user)', loginAs: loginAsAdmin, alpha: true, beta: true },
  { level: 'ExperimentPI (alice)', loginAs: loginAsAlice, alpha: true, beta: false },
  { level: 'ExperimentUser (bob)', loginAs: loginAsBob, alpha: false, beta: true },
  { level: 'Basisrolle (basis-user)', loginAs: loginAsBasisUser, alpha: false, beta: false },
];

for (const { level, loginAs, alpha, beta } of VISIBILITY) {
  test(`${level} sees ${describe(alpha, beta)} of the seeded experiments`, async ({ page }) => {
    await openAppAs(page, loginAs);
    await openExperimentTable(page);

    await expectListed(page, ALPHA, alpha);
    await expectListed(page, BETA, beta);
  });
}

test('Basisrolle (basis-user) sees no experiment at all', async ({ page }) => {
  await openAppAs(page, loginAsBasisUser);

  await openExperimentTable(page);

  await expect(page.locator('.ag-center-cols-container .ag-row')).toHaveCount(0);
});

test('Basisrolle (basis-user) can open the 3D view', async ({ page }) => {
  await openAppAs(page, loginAsBasisUser);
  await page.getByTitle('Measurements').click();

  // The view loads the sensors it places in the scene; row-level security leaves a basic user
  // none, but the call must succeed - a 403 here used to replace the whole view with an error.
  const sensors = page.waitForResponse(
    (response) => new URL(response.url()).pathname === '/api/sensors',
  );
  await page.getByRole('link', { name: '3D View' }).click();

  expect((await sensors).status()).toBe(200);
  // app-giro3d is only rendered when the sensor call did not fail
  await expect(page.locator('app-giro3d')).toBeAttached();
});

test('ExperimentPI (alice) can save an edit on an experiment it may write', async ({ page }) => {
  // A Keycloak admin session, an experiment and its access groups, then a full login.
  test.slow();
  const experiment = await createExperimentWithAccess(GREEK_LETTER, 'alice', 'read + write');
  const comment = `Edited by alice ${crypto.randomUUID().substring(0, 8)}`;

  await openAppAs(page, loginAsAlice);
  await openExperimentTable(page);
  await selectRow(page, experiment.name);
  await page.getByRole('button', { name: 'Edit Experiment' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Comment').fill(comment);
  await dialog.getByRole('button', { name: 'Save', exact: true }).click();

  // the update passes the filter chain and the experiments_update row-level security check
  await expect(page.getByText('Experiment saved successfully.')).toBeVisible();
  expect(await commentOf(experiment.id)).toBe(comment);
});

function describe(alpha: boolean, beta: boolean): string {
  if (alpha && beta) return 'both';
  if (!alpha && !beta) return 'neither';
  return alpha ? 'only Alpha' : 'only Beta';
}

/** Opens the experiment table and waits until the backend answered its first page. */
async function openExperimentTable(page: Page): Promise<void> {
  const firstPage = waitForExperimentPage(page, () => true);
  await page.getByRole('link', { name: 'Experiment' }).click();
  await firstPage;
}

/**
 * Filters the table to `name` and checks whether it is listed. Waits for the filtered response
 * first, so an absent row cannot pass on a request that never came back. A row the caller may not
 * read never shows up at all, so its absence cannot be a stale render either.
 */
async function expectListed(page: Page, name: string, listed: boolean): Promise<void> {
  const filtered = waitForExperimentPage(page, (filterModel) => filterModel.includes(name));
  await page.getByRole('textbox', { name: 'Experiment Name Filter Input' }).fill(name);
  await filtered;

  // Exact: the floating-filter cell holds the same text and would match a substring.
  const nameCell = page.getByRole('gridcell', { name, exact: true });
  if (listed) {
    await expect(nameCell).toBeVisible();
  } else {
    await expect(nameCell).toHaveCount(0);
  }
}

async function selectRow(page: Page, name: string): Promise<void> {
  await expectListed(page, name, true);
  await page.getByRole('gridcell', { name, exact: true }).click();
}

// Only the status: Chromium may already have discarded the body of these grid requests.
async function waitForExperimentPage(
  page: Page,
  matchesFilter: (filterModel: string) => boolean,
): Promise<void> {
  const response = await page.waitForResponse((candidate) => {
    const url = new URL(candidate.url());
    return (
      url.pathname === '/api/experiments' &&
      matchesFilter(url.searchParams.get('filterModel') ?? '')
    );
  });
  expect(response.ok()).toBe(true);
}

async function commentOf(experimentId: string): Promise<string | undefined> {
  const adminApi = await createMonteisApi('admin-user', 'admin-user');
  try {
    const response = await adminApi.get(`/api/experiments/${experimentId}`);
    expect(response.ok()).toBe(true);
    return (await response.json()).comment;
  } finally {
    await adminApi.dispose();
  }
}
