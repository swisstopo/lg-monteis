import { expect, Locator, Page, Response } from '@playwright/test';
import { hasPath } from './responses';

/** Opens the experiment table and waits until the backend answered its first page. */
export async function openExperimentTable(page: Page): Promise<void> {
  const firstPage = expectExperimentPage(page);
  await page.getByRole('link', { name: 'Experiment' }).click();
  await firstPage;
}

/**
 * Filters the table by experiment name, then waits for the filtered response so a missing row
 * can't pass on a request that never came back. A row the caller may not read never shows up, so
 * a missing row can't be left over from an earlier render either.
 */
export async function filterExperimentsByName(page: Page, name: string): Promise<void> {
  const filtered = expectExperimentPage(page, name);
  await page.getByRole('textbox', { name: 'Experiment Name Filter Input' }).fill(name);
  await filtered;
}

/**
 * The name cell of experiment `name`. Matched exactly: the floating-filter cell holds the same
 * text, and a substring match would find it too.
 */
export function experimentNameCell(page: Page, name: string): Locator {
  return page.getByRole('gridcell', { name, exact: true });
}

/** Filters the table down to experiment `name` and selects its row. */
export async function selectExperiment(page: Page, name: string): Promise<void> {
  await filterExperimentsByName(page, name);
  await experimentNameCell(page, name).click();
}

/**
 * Creates experiment `name` through the Create Experiment dialog, which needs an admin. Tests
 * that change an experiment change their own: a seeded one is shared with every other test.
 */
export async function createExperimentInDialog(page: Page, name: string): Promise<void> {
  await page.getByRole('button', { name: 'Create Experiment' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Experiment Name').fill(name);
  await dialog.getByLabel('Start Date').fill('01/01/2030');
  await dialog.getByLabel('End Date').fill('05/05/2030');
  await dialog.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(dialog).toHaveCount(0);
}

export function editExperimentButton(page: Page): Locator {
  return page.getByRole('button', { name: 'Edit Experiment' });
}

export function viewExperimentButton(page: Page): Locator {
  return page.getByRole('button', { name: 'View', exact: true });
}

/**
 * Waits for a page of the experiment table, filtered to `nameFilter` if given, and checks only
 * its status: Chromium may already have dropped the body of these grid requests.
 */
async function expectExperimentPage(page: Page, nameFilter?: string): Promise<void> {
  const response = await page.waitForResponse((candidate) =>
    isExperimentPage(candidate, nameFilter),
  );
  expect(response.ok()).toBe(true);
}

function isExperimentPage(response: Response, nameFilter?: string): boolean {
  return (
    hasPath(response, '/api/experiments') &&
    (nameFilter === undefined || filterModelOf(response).includes(nameFilter))
  );
}

/** The filter model the grid serialises into the request's query string. */
function filterModelOf(response: Response): string {
  return new URL(response.url()).searchParams.get('filterModel') ?? '';
}
