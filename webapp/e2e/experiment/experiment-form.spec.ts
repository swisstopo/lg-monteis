import { expect, test } from '@playwright/test';
import { loginAsAdmin } from '../support/login';

test.beforeEach(async ({ page }) => {
  await page.goto('http://localhost:4200/');
  await loginAsAdmin(page);

  await page.getByRole('link', { name: 'Experiment' }).click();
});

test('should create experiment', async ({ page }) => {
  await page.getByRole('button', { name: 'Create Experiment' }).click();
  await expect(page.getByRole('heading', { name: 'Setup new Experiment', level: 2 })).toBeVisible();

  const dialog = page.getByRole('dialog');

  // Slice unique ID so we don't hit the 50 char max-length validation bounds
  const uniqueId = crypto.randomUUID().substring(0, 8);
  await dialog.getByLabel('Experiment Name').fill(`E2E TEST ${uniqueId}`);

  await dialog.getByLabel('Comment').fill('This is an E2E test experiment comment.');

  // Using dates like 01/01 and 05/05 prevents locale formatting parsing errors in Playwright
  await dialog.getByLabel('Start Date').fill('01/01/2030');
  await dialog.getByLabel('End Date').fill('05/05/2030');

  await dialog.getByRole('button', { name: 'Save', exact: true }).click();

  await expect(page.getByText('Experiment saved successfully.')).toBeVisible();
});

test('should update experiment', async ({ page }) => {
  await page.getByRole('button', { name: 'Edit Experiment' }).click();
  await expect(page.getByRole('heading', { name: 'Edit Experiment', level: 2 })).toBeVisible();

  const dialog = page.getByRole('dialog');

  const uniqueId = crypto.randomUUID().substring(0, 8);
  await dialog.getByLabel('Experiment Name').fill(`E2E TEST UPDATED ${uniqueId}`);

  await dialog.getByLabel('Comment').fill('Updated experiment comment.');

  await dialog.getByLabel('Start Date').fill('02/02/2030');
  await dialog.getByLabel('End Date').fill('04/04/2030');

  await dialog.getByRole('button', { name: 'Save', exact: true }).click();

  await expect(page.getByText('Experiment saved successfully.')).toBeVisible();
});

test('should fail to create existing experiment', async ({ page }) => {
  await page.getByRole('button', { name: 'Create Experiment' }).click();
  await expect(page.getByRole('heading', { name: 'Setup new Experiment', level: 2 })).toBeVisible();

  const dialog = page.getByRole('dialog');

  const uniqueId = crypto.randomUUID().substring(0, 8);
  const experimentName = `E2E TEST ${uniqueId}`;

  await dialog.getByLabel('Experiment Name').fill(experimentName);

  await dialog.getByLabel('Start Date').fill('01/01/2030');
  await dialog.getByLabel('End Date').fill('05/05/2030');

  await dialog.getByRole('button', { name: 'Save and create new', exact: true }).click();

  await expect(page.getByText('Experiment saved successfully.')).toBeVisible();

  // Form should have reset, try to create another experiment with the same name
  await dialog.getByLabel('Experiment Name').fill(experimentName);

  await dialog.getByLabel('Start Date').fill('03/03/2030');
  await dialog.getByLabel('End Date').fill('08/08/2030');

  await dialog.getByRole('button', { name: 'Save', exact: true }).click();

  await expect(page.getByText('An entity with the same code already exists')).toBeVisible();
});

test('should show required validation errors', async ({ page }) => {
  await page.getByRole('button', { name: 'Create Experiment' }).click();
  await expect(page.getByRole('heading', { name: 'Setup new Experiment', level: 2 })).toBeVisible();

  const dialog = page.getByRole('dialog');

  await dialog.getByLabel('Experiment Name').fill('x');
  await dialog.getByLabel('Experiment Name').fill('');
  await dialog.getByLabel('Experiment Name').blur();

  await expect(page.getByText('Experiment name is required')).toBeVisible();
  await expect(dialog.getByRole('button', { name: 'Save', exact: true })).toBeDisabled();
});

test('should reject invalid date bounds', async ({ page }) => {
  await page.getByRole('button', { name: 'Create Experiment' }).click();

  const dialog = page.getByRole('dialog');

  const uniqueId = crypto.randomUUID().substring(0, 8);

  await dialog.getByLabel('Experiment Name').fill(`E2E TEST ${uniqueId}`);

  await dialog.getByLabel('Start Date').fill('05/05/2030');
  await dialog.getByLabel('End Date').fill('01/01/2030');
  await dialog.getByLabel('End Date').blur();

  await expect(page.getByText('End date must be after start date')).toBeVisible();
});

test('should close dialog on cancel', async ({ page }) => {
  await page.getByRole('button', { name: 'Create Experiment' }).click();
  await page.getByRole('button', { name: 'Cancel' }).click();
  await expect(
    page.getByRole('heading', { name: 'Setup new Experiment', level: 2 }),
  ).not.toBeVisible();
});
