import { expect, Locator, Page, test } from '@playwright/test';
import { truncate, writeFile } from 'node:fs/promises';
import {
  MAX_DOCUMENT_SIZE_BYTES,
  MAX_DOCUMENT_SIZE_MB,
} from '../../src/app/features/experiment/experiment-documents/max-document-size';
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

async function upload(
  dialog: Locator,
  name: string,
  content: string,
  mimeType = 'text/plain',
): Promise<void> {
  await chooseFile(dialog, { name, mimeType, buffer: Buffer.from(content) });
}

async function chooseFile(
  dialog: Locator,
  file: string | { name: string; mimeType: string; buffer: Buffer },
): Promise<void> {
  const fileChooserOpened = dialog.page().waitForEvent('filechooser');
  await dialog.getByRole('button', { name: 'Add Document' }).click();
  const fileChooser = await fileChooserOpened;
  await fileChooser.setFiles(file);
}

// a document's script would run on the app's origin, where it can read the viewer's session
const SCRIPT_MARKER = 'monteis-e2e-document-script-ran';
const MARKING_SCRIPT = `<script>localStorage.setItem('${SCRIPT_MARKER}', '1')</script>`;
const HTML_WITH_SCRIPT = `<html><body>${MARKING_SCRIPT}</body></html>`;
const SVG_WITH_SCRIPT = `<svg xmlns="http://www.w3.org/2000/svg">${MARKING_SCRIPT}</svg>`;

async function scriptRan(page: Page): Promise<boolean> {
  return page.evaluate((marker) => localStorage.getItem(marker) !== null, SCRIPT_MARKER);
}

test('uploads a document to an experiment and downloads it', async ({ page }) => {
  const dialog = await editNewExperiment(page);

  await upload(dialog, 'report.txt', 'measured values');

  const fileNameButton = dialog.getByRole('button', { name: 'report.txt' });
  await expect(fileNameButton).toBeVisible();
  await expect(dialog.getByText('admin-user')).toBeVisible();

  const download = page.waitForEvent('download');
  await fileNameButton.click();
  expect((await download).suggestedFilename()).toBe('report.txt');
});

// on its own, WebKit now and then ignores the next click right after a download
test('views a document in a new tab', async ({ page }) => {
  const dialog = await editNewExperiment(page);
  await upload(dialog, 'report.txt', 'measured values');
  await expect(dialog.getByRole('button', { name: 'report.txt' })).toBeVisible();

  const tab = page.context().waitForEvent('page');
  await dialog.getByRole('button', { name: 'View document' }).click();

  await expect(await tab).toHaveURL(/^blob:/);
});

test('rejects an empty file with a toast and lists nothing', async ({ page }) => {
  const dialog = await editNewExperiment(page);

  await upload(dialog, 'empty.txt', '');

  await expect(page.getByText('The file is empty.')).toBeVisible();
  await expect(dialog.getByText('No documents yet.')).toBeVisible();
});

for (const { name, content, mimeType } of [
  { name: 'page.html', content: HTML_WITH_SCRIPT, mimeType: 'text/html' },
  { name: 'drawing.svg', content: SVG_WITH_SCRIPT, mimeType: 'image/svg+xml' },
]) {
  test(`only downloads ${name}, its script never runs`, async ({ page }) => {
    const dialog = await editNewExperiment(page);
    await upload(dialog, name, content, mimeType);
    const fileNameButton = dialog.getByRole('button', { name });
    await expect(fileNameButton).toBeVisible();

    await expect(dialog.getByRole('button', { name: 'View document' })).toHaveCount(0);
    const tabs: Page[] = [];
    page.context().on('page', (tab) => tabs.push(tab));
    const download = page.waitForEvent('download');
    await fileNameButton.click();
    expect((await download).suggestedFilename()).toBe(name);

    expect(tabs).toHaveLength(0);
    expect(await scriptRan(page)).toBe(false);
  });
}

test('runs no script of a document whose upload claims to be an image', async ({ page }) => {
  const dialog = await editNewExperiment(page);
  await upload(dialog, 'page.html', HTML_WITH_SCRIPT, 'image/png');
  await expect(dialog.getByRole('button', { name: 'page.html' })).toBeVisible();

  const tabOpened = page.context().waitForEvent('page');
  await dialog.getByRole('button', { name: 'View document' }).click();
  const tab = await tabOpened;
  await expect(tab).toHaveURL(/^blob:/);
  await tab.waitForLoadState();

  expect(await scriptRan(page)).toBe(false);
});

// through a file: setFiles takes no buffer over 50 MB
test('rejects a file over the size limit with a toast naming the limit', async ({
  page,
}, testInfo) => {
  const dialog = await editNewExperiment(page);
  const path = testInfo.outputPath('too-large.bin');
  await writeFile(path, '');
  await truncate(path, MAX_DOCUMENT_SIZE_BYTES + 1);

  await chooseFile(dialog, path);

  await expect(
    page.getByText(`The file is larger than the allowed ${MAX_DOCUMENT_SIZE_MB} MB.`),
  ).toBeVisible();
  await expect(dialog.getByText('No documents yet.')).toBeVisible();
});
