import { expect, test, type Page } from '@playwright/test';
import { loginAsAdmin } from '../support/login';

// The color rules from the design spec, as the browser reports them via getComputedStyle.
const ALARM_COLOR = 'rgb(194, 0, 9)'; // #c20009
const EXCEEDS_RANGE_COLOR = 'rgb(178, 98, 0)'; // #b26200

// Which state a reading gets is derived by the backend from the sensor parameter's alarm limits
// (see MeasurementState). The seeded readings follow a sine wave against wide alarm limits, so
// which states are present in the 50 newest rows depends on the wall clock at test time - this
// stubs the endpoint instead, so all three states are on screen in a known order. The derivation
// itself is covered by MeasurementStateTest and OverviewQueryRepositoryIT.
const METRICS = [
  {
    timestamp: '2026-07-15T10:10:00Z',
    dasKey: 'E2E__ALARM__value',
    rawValue: 123.0,
    normValue: 120.5,
    version: 0,
    status: 'too_high',
    sensorParameterId: '00000000-0000-7000-8000-0000000004e1',
    measurementState: 'ALARM',
  },
  {
    timestamp: '2026-07-15T10:05:00Z',
    dasKey: 'E2E__RANGE__value',
    rawValue: 19.5,
    normValue: 19.1,
    version: 0,
    status: 'too_low',
    sensorParameterId: '00000000-0000-7000-8000-0000000004e2',
    measurementState: 'EXCEEDS_RANGE',
  },
  {
    timestamp: '2026-07-15T10:00:00Z',
    dasKey: 'E2E__OK__value',
    rawValue: 51.0,
    normValue: 50.0,
    version: 0,
    status: 'correct',
    sensorParameterId: '00000000-0000-7000-8000-0000000004e3',
    measurementState: 'OK',
  },
];

// Ag-grid tags every cell with its column's col-id, which is the only stable hook on a cell.
const valueCell = (page: Page, dasKey: string) =>
  page
    .locator('.ag-row', { has: page.locator('.ag-cell[col-id="dasKey"]', { hasText: dasKey }) })
    .locator('.ag-cell[col-id="normValue"]');

test.beforeEach(async ({ page }) => {
  await page.route('**/api/overview/metrics*', (route) =>
    route.fulfill({ json: METRICS, contentType: 'application/json' }),
  );

  await page.goto('http://localhost:4200/');
  await loginAsAdmin(page);

  await page.getByTitle('Measurements').click();
  await page.getByRole('link', { name: 'Table' }).click();
});

test('should color a value outside the alarm limits red', async ({ page }) => {
  const cell = valueCell(page, 'E2E__ALARM__value');

  await expect(cell).toHaveText('120.5');
  await expect(cell).toHaveCSS('color', ALARM_COLOR);
});

test('should color a value outside the measurement range orange', async ({ page }) => {
  const cell = valueCell(page, 'E2E__RANGE__value');

  await expect(cell).toHaveText('19.1');
  await expect(cell).toHaveCSS('color', EXCEEDS_RANGE_COLOR);
});

test('should leave a value within both limits in the default color', async ({ page }) => {
  const cell = valueCell(page, 'E2E__OK__value');

  await expect(cell).toHaveText('50');
  await expect(cell).not.toHaveClass(/measurement-state-/);

  const color = await cell.evaluate((el) => getComputedStyle(el).color);
  expect([ALARM_COLOR, EXCEEDS_RANGE_COLOR]).not.toContain(color);
});

test('should color only the normalized value, not the raw value', async ({ page }) => {
  const row = page.locator('.ag-row', {
    has: page.locator('.ag-cell[col-id="dasKey"]', { hasText: 'E2E__ALARM__value' }),
  });

  await expect(row.locator('.ag-cell[col-id="normValue"]')).toHaveCSS('color', ALARM_COLOR);
  await expect(row.locator('.ag-cell[col-id="rawValue"]')).not.toHaveCSS('color', ALARM_COLOR);
});
