import { Browser, expect, Locator, Page, test } from '@playwright/test';
import { openAppAs, SEED_USERS } from '../support/login';
import { createSensorInDialog, openSensorTable, selectSensor } from '../support/sensor-table';

// alice is PI of Alpha, bob only reads Beta (docker/keycloak/realm/patch.local.json)
const ALPHA = 'Mont Terri Alpha';
const BETA = 'Mont Terri Beta';

/** Creates a sensor of its own on `mainExperimentName`, as admin in a separate browser context. */
async function createSensor(browser: Browser, mainExperimentName: string): Promise<string> {
  const alias = `SN-DOC-${crypto.randomUUID()}`;
  await asAdmin(browser, async (page) => {
    await openSensorTable(page);
    await createSensorInDialog(page, alias, mainExperimentName);
  });
  return alias;
}

async function asAdmin(browser: Browser, action: (page: Page) => Promise<void>): Promise<void> {
  const context = await browser.newContext();
  try {
    const page = await context.newPage();
    await openAppAs(page, SEED_USERS.admin);
    await action(page);
  } finally {
    await context.close();
  }
}

async function viewSensor(page: Page, alias: string): Promise<Locator> {
  await openSensorTable(page);
  await selectSensor(page, alias);
  await page.getByRole('button', { name: 'View', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('heading', { name: 'View Sensor', level: 2 })).toBeVisible();
  return dialog;
}

async function upload(dialog: Locator, name: string, content: string): Promise<void> {
  const fileChooserOpened = dialog.page().waitForEvent('filechooser');
  await dialog.getByRole('button', { name: 'Add Document' }).click();
  const fileChooser = await fileChooserOpened;
  await fileChooser.setFiles({ name, mimeType: 'text/plain', buffer: Buffer.from(content) });
}

test('a PI uploads a document to a sensor of their experiment and downloads it', async ({
  page,
  browser,
}) => {
  const alias = await createSensor(browser, ALPHA);
  await openAppAs(page, SEED_USERS.alice);
  const dialog = await viewSensor(page, alias);
  await expect(dialog.getByText('No documents yet.')).toBeVisible();
  await expect(dialog.getByRole('textbox', { name: 'DAS Sensor Alias' })).toBeDisabled();

  await upload(dialog, 'calibration.txt', 'calibrated values');

  const fileNameButton = dialog.getByRole('button', { name: 'calibration.txt' });
  await expect(fileNameButton).toBeVisible();
  await expect(dialog.getByText('alice')).toBeVisible();
  const download = page.waitForEvent('download');
  await fileNameButton.click();
  expect((await download).suggestedFilename()).toBe('calibration.txt');
});

test('a reader sees the documents of a sensor but cannot add one', async ({ page, browser }) => {
  const alias = await createSensor(browser, BETA);
  await asAdmin(browser, async (adminPage) => {
    const dialog = await viewSensor(adminPage, alias);
    await upload(dialog, 'manual.txt', 'how to read the sensor');
    await expect(dialog.getByRole('button', { name: 'manual.txt' })).toBeVisible();
  });

  await openAppAs(page, SEED_USERS.bob);
  const dialog = await viewSensor(page, alias);

  await expect(dialog.getByRole('button', { name: 'manual.txt' })).toBeVisible();
  await expect(dialog.getByText('admin-user')).toBeVisible();
  await expect(dialog.getByRole('button', { name: 'Add Document' })).toHaveCount(0);
});
