import { expect, test, type Page } from '@playwright/test';
import { scrollToTableColumn } from '../support/ag-grid';
import { loginAsAdmin } from '../support/login';

// The color rules from the design spec, as the browser reports them via getComputedStyle.
const ALARM_COLOR = 'rgb(194, 0, 9)'; // #c20009
const EXCEEDS_RANGE_COLOR = 'rgb(178, 98, 0)'; // #b26200

const mockMeasurements = [
  {
    sensorParameterId: '00000000-0000-7000-8000-0000000004e2',
    dasKey: 'E2E__ALARM_TOO_HIGH__value',
    experimentName: 'Experiment 1',
    sensorName: 'Sensor 1',
    sensorParameterName: 'Temperature',
    newestMeasurement: '2026-09-23T10:00:00Z',
    measureValue: 100.2,
    unit: '°C',
    sensorType: '...',
    x: 1,
    y: 2,
    z: 3,
    alarmLimitFrom: 10,
    alarmLimitTo: 50,
    active: true,
    comment: null,
    trend: [],
    measurementStatus: 'TOO_HIGH',
  },
  {
    sensorParameterId: '00000000-0000-7000-8000-0000000004e2',
    dasKey: 'E2E__ALARM_TOO_LOW__value',
    experimentName: 'Experiment 1',
    sensorName: 'Sensor 1',
    sensorParameterName: 'Temperature',
    newestMeasurement: '2026-09-23T10:00:00Z',
    measureValue: 5.5,
    unit: '°C',
    sensorType: '...',
    x: 1,
    y: 2,
    z: 3,
    alarmLimitFrom: 10,
    alarmLimitTo: 50,
    active: true,
    comment: null,
    trend: [],
    measurementStatus: 'TOO_LOW',
  },
  {
    sensorParameterId: '00000000-0000-7000-8000-0000000004e2',
    dasKey: 'E2E__CORRECT__value',
    experimentName: 'Experiment 1',
    sensorName: 'Sensor 1',
    sensorParameterName: 'Temperature',
    newestMeasurement: '2026-09-23T10:00:00Z',
    measureValue: 50,
    unit: '°C',
    sensorType: '...',
    x: 1,
    y: 2,
    z: 3,
    alarmLimitFrom: 10,
    alarmLimitTo: 50,
    active: true,
    comment: null,
    trend: [],
    measurementStatus: 'CORRECT',
  },
];

const MEASUREMENTS = {
  rows: mockMeasurements,
  totalCount: 3,
};

// Ag-grid tags every cell with its column's col-id, which is the only stable hook on a cell.
const valueCell = (page: Page, dasKey: string) =>
  page
    .locator('.ag-row', {
      has: page.locator('.ag-cell[col-id="dasKey"]', { hasText: dasKey }),
    })
    .locator('.ag-cell[col-id="measureValue"]');

test.beforeEach(async ({ page }) => {
  await page.route('**/api/measurements*', (route) =>
    route.fulfill({
      json: MEASUREMENTS,
      contentType: 'application/json',
    }),
  );

  await page.goto('http://localhost:4200/');
  await loginAsAdmin(page);

  await page.getByTitle('Measurements').click();
  await page.getByRole('link', { name: 'Table' }).click();
  await scrollToTableColumn(page, 'Value');
});

const alarmTestCases = [
  { expectedColor: ALARM_COLOR, condition: 'ALARM_TOO_HIGH' },
  { expectedColor: ALARM_COLOR, condition: 'ALARM_TOO_LOW' },
];

for (const { expectedColor, condition } of alarmTestCases) {
  test(`should color a value ${condition} the alarm limits correctly`, async ({ page }) => {
    const cell = valueCell(page, `E2E__${condition}__value`);

    await expect(cell).toHaveCSS('color', expectedColor);
  });
}

test('should leave a value within both limits in the default color', async ({ page }) => {
  const cell = valueCell(page, 'E2E__CORRECT__value');

  await expect(cell).not.toHaveClass(/measurement-state-/);

  const color = await cell.evaluate((el) => getComputedStyle(el).color);
  expect([ALARM_COLOR, EXCEEDS_RANGE_COLOR]).not.toContain(color);
});
