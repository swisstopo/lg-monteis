import { expect, test } from '@playwright/test';
import { waitForAutofocus } from '../support/dialog';
import { openAppAs, SEED_USERS } from '../support/login';
import {
  createOrganisationInDialog,
  editOrganisationButton,
  filterOrganisationsByName,
  openCreateOrganisationDialog,
  openOrganisationTable,
  organisationNameCell,
  SEEDED_ORGANISATIONS,
  selectOrganisation,
  uniqueOrganisationName,
} from '../support/organisation-table';
import { expectTableToolbar } from '../support/table';

test.beforeEach(async ({ page }) => {
  await openAppAs(page, SEED_USERS.admin);
  await openOrganisationTable(page);
});

test('should show the toolbar and the seeded organisations', async ({ page }) => {
  await expectTableToolbar(page);
  await expect(page.getByRole('button', { name: 'Create Organisation' })).toBeVisible();
  await expect(editOrganisationButton(page)).toBeDisabled();

  for (const name of Object.values(SEEDED_ORGANISATIONS)) {
    await filterOrganisationsByName(page, name);
    await expect(organisationNameCell(page, name)).toHaveCount(1);
  }
});

test('should enable edit only while an organisation is selected', async ({ page }) => {
  await expect(editOrganisationButton(page)).toBeDisabled();

  await selectOrganisation(page, SEEDED_ORGANISATIONS.swisstopo);
  await expect(editOrganisationButton(page)).toBeEnabled();

  await organisationNameCell(page, SEEDED_ORGANISATIONS.swisstopo).click({
    modifiers: ['ControlOrMeta'],
  });
  await expect(editOrganisationButton(page)).toBeDisabled();
});

test('should create an organisation and list it', async ({ page }) => {
  const name = uniqueOrganisationName();
  const dialog = await openCreateOrganisationDialog(page);
  await dialog.getByLabel('Organisation Name').fill(name);
  await dialog.getByLabel('Comment').fill('This is an E2E test organisation.');

  await dialog.getByRole('button', { name: 'Save', exact: true }).click();

  await expect(page.getByText('Organisation saved successfully.')).toBeVisible();
  await expect(dialog).toHaveCount(0);
  await filterOrganisationsByName(page, name);
  await expect(organisationNameCell(page, name)).toHaveCount(1);
});

test('should update an organisation', async ({ page }) => {
  const name = uniqueOrganisationName();
  await createOrganisationInDialog(page, name);
  await selectOrganisation(page, name);
  await editOrganisationButton(page).click();
  await expect(page.getByRole('heading', { name: 'Edit Organisation', level: 2 })).toBeVisible();
  const dialog = page.getByRole('dialog');
  await waitForAutofocus(dialog);
  await expect(dialog.getByLabel('Organisation Name')).toHaveValue(name);

  const updatedName = uniqueOrganisationName();
  await dialog.getByLabel('Organisation Name').fill(updatedName);
  await dialog.getByLabel('Comment').fill('Updated organisation comment.');
  await dialog.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(dialog).toHaveCount(0);

  await filterOrganisationsByName(page, updatedName);
  await expect(organisationNameCell(page, updatedName)).toHaveCount(1);
  await filterOrganisationsByName(page, name);
  await expect(organisationNameCell(page, name)).toHaveCount(0);
});

test('should show required validation errors', async ({ page }) => {
  const dialog = await openCreateOrganisationDialog(page);

  await dialog.getByLabel('Organisation Name').fill('x');
  await expect(page.getByText('Name must be at least 2 characters long')).toBeVisible();
  await dialog.getByLabel('Organisation Name').fill('');
  await dialog.getByLabel('Organisation Name').blur();

  await expect(page.getByText('Organisation name is required')).toBeVisible();
  await expect(dialog.getByRole('button', { name: 'Save', exact: true })).toBeDisabled();
});
