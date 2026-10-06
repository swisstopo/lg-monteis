import { expect, test } from '@playwright/test';
import { waitForAutofocus } from '../support/dialog';
import {
  createExperimentInDialog,
  editExperimentButton,
  fillRequiredExperimentFields,
  openCreateExperimentDialog,
  openExperimentTable,
  selectExperiment,
} from '../support/experiment-table';
import { uniqueExperimentName } from '../support/experiments';
import { openAppAs, SEED_USERS } from '../support/login';

// day and month are the same number, the dialog parses them alike in every date locale
const UPDATED_PERIOD = { start: '02/02/2030', end: '04/04/2030' };

test.beforeEach(async ({ page }) => {
  await openAppAs(page, SEED_USERS.admin);
  await openExperimentTable(page);
});

test('should create experiment', async ({ page }) => {
  const dialog = await openCreateExperimentDialog(page);
  await fillRequiredExperimentFields(dialog, uniqueExperimentName());
  await dialog.getByLabel('Comment').fill('This is an E2E test experiment comment.');

  await dialog.getByRole('button', { name: 'Save', exact: true }).click();

  await expect(page.getByText('Experiment saved successfully.')).toBeVisible();
});

test('should update experiment', async ({ page }) => {
  const name = uniqueExperimentName();
  await createExperimentInDialog(page, name);
  await selectExperiment(page, name);
  await editExperimentButton(page).click();
  await expect(page.getByRole('heading', { name: 'Edit Experiment', level: 2 })).toBeVisible();
  const dialog = page.getByRole('dialog');
  await waitForAutofocus(dialog);

  const updatedName = uniqueExperimentName();
  await dialog.getByLabel('Experiment Name').fill(updatedName);
  await dialog.getByLabel('Comment').fill('Updated experiment comment.');
  await dialog.getByLabel('Start Date').fill(UPDATED_PERIOD.start);
  await dialog.getByLabel('End Date').fill(UPDATED_PERIOD.end);
  await dialog.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(dialog).toHaveCount(0);

  await selectExperiment(page, updatedName);
});

test('should fail to create existing experiment', async ({ page }) => {
  const name = uniqueExperimentName();
  const dialog = await openCreateExperimentDialog(page);
  await fillRequiredExperimentFields(dialog, name);
  await dialog.getByRole('button', { name: 'Save and create new', exact: true }).click();
  await expect(page.getByText('Experiment saved successfully.')).toBeVisible();

  // the form is empty again for the next experiment, try the same name
  await fillRequiredExperimentFields(dialog, name);
  await dialog.getByRole('button', { name: 'Save', exact: true }).click();

  await expect(page.getByText('An entity with the same code already exists')).toBeVisible();
});

test('should show required validation errors', async ({ page }) => {
  const dialog = await openCreateExperimentDialog(page);

  await dialog.getByLabel('Experiment Name').fill('x');
  await dialog.getByLabel('Experiment Name').fill('');
  await dialog.getByLabel('Experiment Name').blur();

  await expect(page.getByText('Experiment name is required')).toBeVisible();
  await expect(dialog.getByRole('button', { name: 'Save', exact: true })).toBeDisabled();
});

test('should reject invalid date bounds', async ({ page }) => {
  const dialog = await openCreateExperimentDialog(page);
  await dialog.getByLabel('Experiment Name').fill(uniqueExperimentName());

  await dialog.getByLabel('Start Date').fill('05/05/2030');
  await dialog.getByLabel('End Date').fill('01/01/2030');
  await dialog.getByLabel('End Date').blur();

  await expect(page.getByText('End date must not be before start date')).toBeVisible();
});

test('should close dialog on cancel', async ({ page }) => {
  const dialog = await openCreateExperimentDialog(page);

  await dialog.getByRole('button', { name: 'Cancel' }).click();

  await expect(
    page.getByRole('heading', { name: 'Setup new Experiment', level: 2 }),
  ).not.toBeVisible();
});
