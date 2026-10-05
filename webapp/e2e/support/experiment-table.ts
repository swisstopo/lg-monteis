import { expect, Locator, Page } from '@playwright/test';
import { waitForAutofocus } from './dialog';
import { E2E_EXPERIMENT_DIALOG_PERIOD } from './experiments';
import { expectGridPage, filterGrid, gridCell } from './grid';

const EXPERIMENTS = '/api/experiments';

/** Opens the experiment table and waits until the backend answered its first page. */
export async function openExperimentTable(page: Page): Promise<void> {
  const firstPage = expectGridPage(page, EXPERIMENTS);
  await page.getByRole('link', { name: 'Experiment' }).click();
  await firstPage;
}

/** Filters the table by experiment name and waits for the filtered page. */
export async function filterExperimentsByName(page: Page, name: string): Promise<void> {
  await filterGrid(page, EXPERIMENTS, 'Experiment Name Filter Input', name);
}

export function experimentNameCell(page: Page, name: string): Locator {
  return gridCell(page, name);
}

/** Filters the table down to experiment `name` and selects its row. */
export async function selectExperiment(page: Page, name: string): Promise<void> {
  await filterExperimentsByName(page, name);
  await experimentNameCell(page, name).click();
}

/** Opens the Create Experiment dialog, ready to be filled. */
export async function openCreateExperimentDialog(page: Page): Promise<Locator> {
  await page.getByRole('button', { name: 'Create Experiment' }).click();
  await expect(page.getByRole('heading', { name: 'Setup new Experiment', level: 2 })).toBeVisible();
  const dialog = page.getByRole('dialog');
  await waitForAutofocus(dialog);
  return dialog;
}

/** Fills name and the e2e period, what a new experiment needs. */
export async function fillRequiredExperimentFields(dialog: Locator, name: string): Promise<void> {
  await dialog.getByLabel('Experiment Name').fill(name);
  await dialog.getByLabel('Start Date').fill(E2E_EXPERIMENT_DIALOG_PERIOD.start);
  await dialog.getByLabel('End Date').fill(E2E_EXPERIMENT_DIALOG_PERIOD.end);
}

/**
 * Creates experiment `name` through the Create Experiment dialog, which needs an admin. Tests
 * that change an experiment change their own: a seeded one is shared with every other test.
 */
export async function createExperimentInDialog(page: Page, name: string): Promise<void> {
  const dialog = await openCreateExperimentDialog(page);
  await fillRequiredExperimentFields(dialog, name);
  await dialog.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(dialog).toHaveCount(0);
}

export function editExperimentButton(page: Page): Locator {
  return page.getByRole('button', { name: 'Edit Experiment' });
}

export function viewExperimentButton(page: Page): Locator {
  return page.getByRole('button', { name: 'View', exact: true });
}
