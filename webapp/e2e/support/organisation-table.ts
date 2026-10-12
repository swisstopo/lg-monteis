import { expect, Locator, Page } from '@playwright/test';
import { waitForAutofocus } from './dialog';
import { randomSuffix } from './experiments';
import { expectGridPage, filterGrid, gridCell } from './grid';

const ORGANISATIONS = '/api/organisations';

/**
 * Organisations of db/meta/seed/R__seed_dev_data.sql. Names are unique case-insensitively and
 * these tests never delete what they create, so a test that changes one changes its own.
 */
export const SEEDED_ORGANISATIONS = { swisstopo: 'swisstopo', eth: 'ETH Zurich' };

/** A fresh organisation name, short enough for the 100 character limit. */
export function uniqueOrganisationName(): string {
  return `E2E-ORG-${randomSuffix()}`;
}

/** Opens the organisation table through the admin menu and waits for its first page. */
export async function openOrganisationTable(page: Page): Promise<void> {
  const firstPage = expectGridPage(page, ORGANISATIONS);
  await page.getByTitle('Admin').click();
  await page.getByRole('link', { name: 'Organisations' }).click();
  await firstPage;
}

/** Filters the table by organisation name and waits for the filtered page. */
export async function filterOrganisationsByName(page: Page, name: string): Promise<void> {
  await filterGrid(page, ORGANISATIONS, 'Organisation Name Filter Input', name);
}

export function organisationNameCell(page: Page, name: string): Locator {
  return gridCell(page, name);
}

/** Filters the table down to organisation `name` and selects its row. */
export async function selectOrganisation(page: Page, name: string): Promise<void> {
  await filterOrganisationsByName(page, name);
  await organisationNameCell(page, name).click();
}

export function editOrganisationButton(page: Page): Locator {
  return page.getByRole('button', { name: 'Edit Organisation' });
}

/** Opens the Create Organisation dialog, ready to be filled. */
export async function openCreateOrganisationDialog(page: Page): Promise<Locator> {
  await page.getByRole('button', { name: 'Create Organisation' }).click();
  await expect(page.getByRole('heading', { name: 'Create Organisation', level: 2 })).toBeVisible();
  const dialog = page.getByRole('dialog');
  await waitForAutofocus(dialog);
  return dialog;
}

/** Creates organisation `name` through the Create Organisation dialog, which needs an admin. */
export async function createOrganisationInDialog(page: Page, name: string): Promise<void> {
  const dialog = await openCreateOrganisationDialog(page);
  await dialog.getByLabel('Organisation Name').fill(name);
  await dialog.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(dialog).toHaveCount(0);
}
