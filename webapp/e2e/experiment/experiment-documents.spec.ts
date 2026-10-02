import { expect, test } from '@playwright/test';
import {
  editExperimentButton,
  openExperimentTable,
  selectExperiment,
} from '../support/experiment-table';
import { uniqueExperimentName } from '../support/experiments';
import { openAppAs, SEED_USERS } from '../support/login';

test('uploads a document to an experiment and downloads it again', async ({ page }) => {
  const name = uniqueExperimentName();
  await openAppAs(page, SEED_USERS.admin);
  await openExperimentTable(page);

  await page.getByRole('button', { name: 'Create Experiment' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Experiment Name').fill(name);
  await dialog.getByLabel('Start Date').fill('01/01/2030');
  await dialog.getByLabel('End Date').fill('05/05/2030');
  await dialog.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(dialog).toHaveCount(0);

  await selectExperiment(page, name);
  await editExperimentButton(page).click();
  await expect(dialog.getByText('No documents yet.')).toBeVisible();

  const fileChooser = page.waitForEvent('filechooser');
  await dialog.getByRole('button', { name: 'Add Document' }).click();
  await (
    await fileChooser
  ).setFiles({
    name: 'report.txt',
    mimeType: 'text/plain',
    buffer: Buffer.from('measured values'),
  });

  const documentLink = dialog.getByRole('link', { name: 'report.txt' });
  await expect(documentLink).toBeVisible();

  const download = page.waitForEvent('download');
  await documentLink.click();
  expect((await download).suggestedFilename()).toBe('report.txt');
});
