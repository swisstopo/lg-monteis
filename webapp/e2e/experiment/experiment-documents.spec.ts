import { expect, Locator, Page, test } from '@playwright/test';
import {
  createExperimentInDialog,
  editExperimentButton,
  openExperimentTable,
  selectExperiment,
} from '../support/experiment-table';
import { uniqueExperimentName } from '../support/experiments';
import { openAppAs, SEED_USERS } from '../support/login';

/** Opens a fresh experiment of its own in the edit dialog, as admin. */
async function editNewExperiment(page: Page): Promise<Locator> {
  const name = uniqueExperimentName();
  await openAppAs(page, SEED_USERS.admin);
  await openExperimentTable(page);
  await createExperimentInDialog(page, name);
  await selectExperiment(page, name);
  await editExperimentButton(page).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByText('No documents yet.')).toBeVisible();
  return dialog;
}

async function upload(page: Page, dialog: Locator, name: string, content: string): Promise<void> {
  const fileChooser = page.waitForEvent('filechooser');
  await dialog.getByRole('button', { name: 'Add Document' }).click();
  await (
    await fileChooser
  ).setFiles({
    name,
    mimeType: 'text/plain',
    buffer: Buffer.from(content),
  });
}

test('uploads a document to an experiment, downloads and views it', async ({ page }) => {
  const dialog = await editNewExperiment(page);

  await upload(page, dialog, 'report.txt', 'measured values');

  const documentLink = dialog.getByRole('link', { name: 'report.txt' });
  await expect(documentLink).toBeVisible();
  await expect(dialog.getByText('admin-user')).toBeVisible();

  const download = page.waitForEvent('download');
  await documentLink.click();
  expect((await download).suggestedFilename()).toBe('report.txt');

  const tab = page.context().waitForEvent('page');
  await dialog.getByRole('button', { name: 'View document' }).click();
  await expect(await tab).toHaveURL(/^blob:/);
});

test('rejects an empty file with a toast and lists nothing', async ({ page }) => {
  const dialog = await editNewExperiment(page);

  await upload(page, dialog, 'empty.txt', '');

  await expect(page.getByText('The file is empty.')).toBeVisible();
  await expect(dialog.getByText('No documents yet.')).toBeVisible();
});
