import { expect, test } from '@playwright/test';
import { loginAsAdmin } from '../support/login';

test.beforeEach(async ({ page }) => {
  await page.goto('http://localhost:4200/');
  await loginAsAdmin(page);

  await page.getByRole('button', { name: 'data_exploration' }).click();
  await page.getByRole('link', { name: 'Measurements' }).click();
});

test('should create a chart from a selected date range', async ({ page }) => {
  await page.getByPlaceholder('Start date').fill('2026-07-01');
  await page.getByPlaceholder('End date').fill('2026-07-31');

  await page.getByRole('button', { name: 'Plot' }).click();

  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('heading', { level: 2 })).toContainText('to');
  await expect(dialog.locator('canvas')).toBeAttached();

  await dialog.getByRole('button', { name: 'Close' }).click();
  await expect(dialog).not.toBeVisible();
});

test('should require a date range before plotting', async ({ page }) => {
  await page.getByPlaceholder('Start date').fill('x');
  await page.getByPlaceholder('Start date').fill('');
  await page.getByPlaceholder('Start date').blur();

  await expect(page.getByText('Start date is required')).toBeVisible();
});

test('should render the chart toolbar with zoom controls', async ({ page }) => {
  await page.getByPlaceholder('Start date').fill('2026-07-01');
  await page.getByPlaceholder('End date').fill('2026-07-31');

  await page.getByRole('button', { name: 'Plot' }).click();

  const dialog = page.getByRole('dialog');
  const toolbar = dialog.locator('app-chart-toolbar');
  const buttons = toolbar.locator('button');

  await expect(toolbar).toBeVisible();
  await expect(buttons).toHaveCount(5);

  const icons = toolbar.locator('mat-icon');
  await expect(icons.nth(0)).toHaveAttribute('fontIcon', 'highlight_alt');
  await expect(icons.nth(1)).toHaveAttribute('fontIcon', 'zoom_in');
  await expect(icons.nth(2)).toHaveAttribute('fontIcon', 'zoom_out');
  await expect(icons.nth(3)).toHaveAttribute('fontIcon', 'search_off');
  await expect(icons.nth(4)).toHaveAttribute('fontIcon', 'file_download');

  await buttons.nth(3).click();
  await dialog.getByRole('button', { name: 'Close' }).click();
  await expect(dialog).not.toBeVisible();
});
