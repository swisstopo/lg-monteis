import { expect, Locator, Page, test } from '@playwright/test';
import { pickAutocompleteOption, waitForAutofocus } from '../support/dialog';
import {
  createExperimentInDialog,
  editExperimentButton,
  openCreateExperimentDialog,
  openExperimentTable,
  selectExperiment,
} from '../support/experiment-table';
import { SEEDED_EXPERIMENTS, uniqueExperimentName } from '../support/experiments';
import { openAppAs, SEED_USERS } from '../support/login';

// alice is the one PI of Alpha in docker/keycloak/realm/patch.local.json
const ALICE = 'Alice Example';
const ALICE_OPTION = 'Alice Example (alice@example.test)';

async function openEditDialog(page: Page, name: string): Promise<Locator> {
  await selectExperiment(page, name);
  await editExperimentButton(page).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('heading', { name: 'Edit Experiment', level: 2 })).toBeVisible();
  await waitForAutofocus(dialog);
  return dialog;
}

test.describe('as admin', () => {
  test.beforeEach(async ({ page }) => {
    await openAppAs(page, SEED_USERS.admin);
    await openExperimentTable(page);
  });

  test('a new experiment shows the owners disabled with the reason', async ({ page }) => {
    const dialog = await openCreateExperimentDialog(page);

    await expect(dialog.getByTestId('owners-create').getByRole('textbox')).toBeDisabled();
    await expect(dialog.getByTestId('owners-create-hint')).toHaveText(
      'Nobody has write access to a new experiment yet. Save it first, owners can be assigned once people have write access.',
    );
  });

  test('an experiment nobody has write access to offers no owners', async ({ page }) => {
    const name = uniqueExperimentName();
    await createExperimentInDialog(page, name);

    const dialog = await openEditDialog(page, name);

    await expect(dialog.getByText('Nobody has write access to this experiment yet.')).toBeVisible();
  });

  test('assigns a PI as owner and shows it in the table', async ({ page }) => {
    // every browser project assigns the same owner to the shared Alpha, they all end up alike
    const dialog = await openEditDialog(page, SEEDED_EXPERIMENTS.alpha);
    const input = dialog.getByTestId('owner-input');
    await expect(input).toBeEnabled();
    await expect(
      dialog.getByText('Only people with write access to this experiment can be owners.'),
    ).toBeVisible();
    const aliceChip = dialog.getByTestId('selected-owner').filter({ hasText: ALICE });
    if (await aliceChip.count()) {
      await aliceChip.getByRole('button', { name: 'Remove owner' }).click();
    }

    await pickAutocompleteOption(input, 'alice', ALICE_OPTION);
    await expect(aliceChip).toBeVisible();
    await dialog.getByRole('button', { name: 'Save', exact: true }).click();
    await expect(dialog).toHaveCount(0);

    // the table still shows Alpha only and reloads it after the save
    await expect(page.getByRole('gridcell', { name: ALICE })).toBeVisible();
  });
});

test('a PI who is no admin sees the owners read-only', async ({ page }) => {
  await openAppAs(page, SEED_USERS.alice);
  await openExperimentTable(page);

  const dialog = await openEditDialog(page, SEEDED_EXPERIMENTS.alpha);

  await expect(dialog.getByTestId('experiment-owner-list')).toBeVisible();
  await expect(dialog.getByTestId('owner-input')).toHaveCount(0);
});
