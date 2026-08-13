import { expect, test, type Page } from '@playwright/test';
import { loginAsAdmin } from '../support/login';

// Both time fields use the same 'HH:mm' placeholder, so locate all four range fields by their
// label instead (matching the convention in e2e/sensor/sensor-form.spec.ts).
const startDate = (page: Page) => page.getByLabel('Start date', { exact: true });
const endDate = (page: Page) => page.getByLabel('End date', { exact: true });
const startTime = (page: Page) => page.getByLabel('Start time', { exact: true });
const endTime = (page: Page) => page.getByLabel('End time', { exact: true });

async function fillRange(page: Page): Promise<void> {
  await startDate(page).fill('2026-07-01');
  await endDate(page).fill('2026-07-31');
  await startTime(page).fill('08:00');
  await endTime(page).fill('18:30');
}

test.beforeEach(async ({ page }) => {
  await page.goto('http://localhost:4200/');
  await loginAsAdmin(page);

  await page.getByRole('button', { name: 'data_exploration' }).click();
  await page.getByRole('link', { name: 'Measurements' }).click();
});

test('should create a chart from a selected date range', async ({ page }) => {
  await fillRange(page);

  await page.getByRole('button', { name: 'Plot' }).click();

  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('heading', { level: 2 })).toContainText('to');
  await expect(dialog.locator('canvas')).toBeAttached();

  await dialog.getByRole('button', { name: 'Close' }).click();
  await expect(dialog).not.toBeVisible();
});

test('should require a date range before plotting', async ({ page }) => {
  await startDate(page).fill('x');
  await startDate(page).fill('');
  await startDate(page).blur();

  await expect(page.getByText('Start date is required')).toBeVisible();
});

test('should default the time fields to a full 24h day in HH:mm format', async ({ page }) => {
  await expect(startTime(page)).toHaveValue('00:00');
  await expect(endTime(page)).toHaveValue('23:59');
});

test('should keep typed times in 24h format instead of converting them to AM/PM', async ({
  page,
}) => {
  await startTime(page).fill('21:30');
  await startTime(page).blur();

  await expect(startTime(page)).toHaveValue('21:30');
});

test('should reject an AM/PM time as invalid', async ({ page }) => {
  await startTime(page).fill('9:30 PM');
  await startTime(page).blur();

  await expect(startTime(page)).toHaveAttribute('aria-invalid', 'true');
});

test('should offer 24h formatted options in the timepicker dropdown', async ({ page }) => {
  // Default aria-label of MatTimepickerToggle; .first() targets the "Start time" toggle.
  await page.getByRole('button', { name: 'Open timepicker options' }).first().click();

  const options = page.getByRole('option');
  await expect(options.first()).toBeVisible();

  for (const label of await options.allInnerTexts()) {
    expect(label.trim()).toMatch(/^\d{2}:\d{2}$/);
  }
});

test('should render the chart toolbar with zoom controls', async ({ page }) => {
  await fillRange(page);

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
