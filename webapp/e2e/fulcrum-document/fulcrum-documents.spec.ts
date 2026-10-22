import { expect, Page, test } from '@playwright/test';
import {
  ensureFulcrumSynced,
  openFulcrumDocumentTable,
  STUB_FILES,
  waitForFulcrumSyncToEnd,
} from '../support/fulcrum-documents';
import { gridCell } from '../support/grid';
import { label, openAppAs, SEED_USERS } from '../support/login';
import { createMonteisApi } from '../support/monteis-api';
import { dataRows } from '../support/table';

// one sync for the file: a refresh while another test's sync runs would answer "already running"
test.describe.configure({ mode: 'default' });

test.beforeAll(async () => {
  const api = await createMonteisApi(SEED_USERS.admin.username, SEED_USERS.admin.password);
  await ensureFulcrumSynced(api);
  await api.dispose();
});

function searchBox(page: Page) {
  return page.getByRole('searchbox', { name: 'Search' });
}

async function selectDocument(page: Page, fileName: string): Promise<void> {
  await gridCell(page, fileName).click();
}

test.describe(`as ${label(SEED_USERS.admin)}`, () => {
  test.beforeEach(async ({ page }) => {
    await openAppAs(page, SEED_USERS.admin);
    await openFulcrumDocumentTable(page);
  });

  test('lists the Fulcrum Documents with the first file of each', async ({ page }) => {
    await expect(gridCell(page, STUB_FILES.opalinusReport)).toBeVisible();
    await expect(gridCell(page, STUB_FILES.gasMeasurements)).toBeVisible();
    await expect(gridCell(page, STUB_FILES.nicheMonitoring)).toBeVisible();
    await expect(gridCell(page, STUB_FILES.nicheMonitoringAppendix)).toHaveCount(0);
    await expect(page.getByText(/Last successful sync at/)).toBeVisible();
  });

  for (const { by, term, found } of [
    { by: 'file name', term: 'gas-measurements', found: STUB_FILES.gasMeasurements },
    { by: 'keyword', term: 'swelling', found: STUB_FILES.opalinusReport },
    { by: 'every word', term: 'nussbaum 2021', found: STUB_FILES.nicheMonitoring },
  ]) {
    test(`finds a document by ${by}`, async ({ page }) => {
      await searchBox(page).fill(term);

      await expect(gridCell(page, found)).toBeVisible();
      await expect(dataRows(page)).toHaveCount(1);
    });
  }

  test('finds nothing when one of the words matches no document', async ({ page }) => {
    await searchBox(page).fill('nussbaum granite');

    await expect(dataRows(page)).toHaveCount(0);
  });

  test('views a pdf in a new tab', async ({ page }) => {
    await selectDocument(page, STUB_FILES.opalinusReport);

    const tab = page.context().waitForEvent('page');
    await page.getByRole('button', { name: 'View', exact: true }).click();

    await expect(await tab).toHaveURL(/^blob:/);
  });

  test('only downloads a file the browser must not show', async ({ page }) => {
    await selectDocument(page, STUB_FILES.gasMeasurements);

    await expect(page.getByRole('button', { name: 'View', exact: true })).toBeDisabled();
    const download = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Download', exact: true }).click();
    expect((await download).suggestedFilename()).toBe(STUB_FILES.gasMeasurements);
  });

  // a fresh context: the workbench of this one would restore the view without loading it again
  test('starts a sync on refresh and shows it in a newly opened overview', async ({
    page,
    browser,
  }) => {
    const lastSync = page.getByText(/Last successful sync at/);
    const before = await lastSync.textContent();

    await page.getByRole('button', { name: 'Refresh', exact: true }).click();

    await expect(page.getByText(/Sync with Fulcrum started/)).toBeVisible();
    const api = await createMonteisApi(SEED_USERS.admin.username, SEED_USERS.admin.password);
    await waitForFulcrumSyncToEnd(api);
    await api.dispose();
    const context = await browser.newContext();
    const reopened = await context.newPage();
    await openAppAs(reopened, SEED_USERS.admin);
    await openFulcrumDocumentTable(reopened);
    await expect(reopened.getByText(/Last successful sync at/)).not.toHaveText(before ?? '');
    await context.close();
  });
});

test.describe(`as ${label(SEED_USERS.basisUser)}`, () => {
  test('lists the documents but offers no file or sync action', async ({ page }) => {
    await openAppAs(page, SEED_USERS.basisUser);
    await openFulcrumDocumentTable(page);

    await expect(gridCell(page, STUB_FILES.opalinusReport)).toBeVisible();
    await expect(dataRows(page)).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'View', exact: true })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Download', exact: true })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Refresh', exact: true })).toHaveCount(0);
  });
});
