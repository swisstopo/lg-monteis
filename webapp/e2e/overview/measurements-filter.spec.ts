import { expect, test, type Locator, type Page } from '@playwright/test';
import { loginAsAdmin } from '../support/login';

// Seeded DAS keys (db/timescale/seed/R__seed_dev_data.sql): every reading belongs to one of five
// sensors, so "TEM" matches the temperature sensor's rows only and never the flow ones.
const TEMPERATURE_KEY = 'SOL_EXPERTS__TEMP-1__temperature';
const PAGED_METRICS_PATH = '/api/overview/metrics/paged';

// Long enough to cover the search's 300ms debounce plus the request it would trigger, used where
// the assertion is that nothing loads.
const NO_LOAD_GRACE_MS = 1500;

const dasKeySearch = (page: Page): Locator =>
  page.locator('.ag-header-cell[col-id="dasKey"] app-min-chars-text-filter input');

// The number floating filter renders a second, disabled input for the "in range" upper bound, so
// this picks the number field the user actually types into.
const rawValueFilter = (page: Page): Locator =>
  page.locator('.ag-header-cell[col-id="rawValue"] input.ag-number-field-input');

// Rows whose block the infinite row model has not fetched yet render as blank placeholder cells,
// which would otherwise count as rows "not matching the search".
const loadedCells = (page: Page, colId: string): Locator =>
  page.locator(`.ag-row:not(.ag-row-loading) .ag-cell[col-id="${colId}"]`);

const dasKeyCells = (page: Page): Locator => loadedCells(page, 'dasKey');

/** The filterModel the table sent with its most recent page request, parsed back from the URL. */
function filterModelOf(requestUrl: string): Record<string, unknown> {
  const raw = new URL(requestUrl).searchParams.get('filterModel');
  return raw ? JSON.parse(raw) : {};
}

function recordPageRequests(page: Page): string[] {
  const urls: string[] = [];
  page.on('request', (request) => {
    if (request.url().includes(PAGED_METRICS_PATH)) urls.push(request.url());
  });
  return urls;
}

test.beforeEach(async ({ page }) => {
  // Each test logs in and waits for a full grid load; the default 30s is not enough for that on
  // every browser when the suite runs its three projects in parallel.
  test.setTimeout(60_000);

  await page.goto('http://localhost:4200/');
  await loginAsAdmin(page);

  await page.getByTitle('Measurements').click();
  await page.getByRole('link', { name: 'Table' }).click();

  // Cold load: the first page request also runs the row count over the readings hypertable, which
  // outlasts the default 5s expect timeout on a loaded machine.
  await expect(dasKeyCells(page).first()).toBeVisible({ timeout: 20_000 });
});

// AK: "Die Suche lädt dynamisch ab der Eingabe von mind. 3 Buchstaben oder Ziffern."
test('should not search while fewer than three characters are typed', async ({ page }) => {
  const requests = recordPageRequests(page);

  await dasKeySearch(page).fill('TE');
  await page.waitForTimeout(NO_LOAD_GRACE_MS);

  expect(requests.filter((url) => filterModelOf(url).dasKey)).toHaveLength(0);
  // Rows of other sensors are still there, i.e. the table was not filtered behind the user's back.
  await expect(dasKeyCells(page).filter({ hasText: 'FLOW' }).first()).toBeVisible();
});

// AK: "Die Suche lädt dynamisch ab der Eingabe von mind. 3 Buchstaben oder Ziffern." +
// "Es werden nur Ergebnisse, die der Suche entsprechen angezeigt."
test('should search from the third character on and show matching rows only', async ({ page }) => {
  const searched = page.waitForRequest((request) =>
    Boolean(filterModelOf(request.url()).dasKey ?? false),
  );

  await dasKeySearch(page).fill('TEM');

  await searched;
  await expect(dasKeyCells(page).first()).toHaveText(TEMPERATURE_KEY);
  await expect(dasKeyCells(page).filter({ hasNotText: 'TEMP' })).toHaveCount(0);
});

// AK: "Die Suche / Das Filtern wird nur in der Spalte angewendet."
test('should apply the search to the searched column only', async ({ page }) => {
  const searched = page.waitForRequest((request) =>
    Boolean(filterModelOf(request.url()).dasKey ?? false),
  );

  await dasKeySearch(page).fill('TEM');
  const request = await searched;

  expect(Object.keys(filterModelOf(request.url()))).toEqual(['dasKey']);
  await expect(rawValueFilter(page)).toHaveValue('');
});

// AK: "Es werden nur Ergebnisse, die der Suche entsprechen angezeigt." - shortening the term back
// below the threshold is no longer a search, so the full table comes back.
test('should drop the search once the term is shortened below three characters', async ({
  page,
}) => {
  await dasKeySearch(page).fill('TEM');
  await expect(dasKeyCells(page).filter({ hasNotText: 'TEMP' })).toHaveCount(0);

  const searchDropped = page.waitForRequest(
    (request) => request.url().includes(PAGED_METRICS_PATH) && !filterModelOf(request.url()).dasKey,
  );
  await dasKeySearch(page).fill('TE');
  await searchDropped;

  await expect(dasKeyCells(page).filter({ hasText: 'FLOW' }).first()).toBeVisible();
});

// AK: "Das Filtern lädt dynamisch." - no Apply button anywhere, the filter runs on input.
test('should filter a value column dynamically, without an apply step', async ({ page }) => {
  const filtered = page.waitForRequest((request) =>
    Boolean(filterModelOf(request.url()).rawValue ?? false),
  );

  await rawValueFilter(page).fill('1');

  await filtered;
  await expect(page.getByRole('button', { name: 'Apply' })).toHaveCount(0);
});

// AK: "Die Suche / Das Filtern wird nur in der Spalte angewendet." + "Es werden nur Ergebnisse,
// die der Suche/dem Filtern entsprechen angezeigt." - filtering two columns narrows to the rows
// matching both, rather than one condition replacing the other.
test('should combine the conditions of several columns', async ({ page }) => {
  await dasKeySearch(page).fill('TEM');
  await expect(dasKeyCells(page).filter({ hasNotText: 'TEMP' })).toHaveCount(0);

  // Seeded raw values follow 50 + 30*sin(...), so "> 70" always matches some temperature
  // readings and never all of them. The operator lives in the popup - the floating filter row
  // carries the value only.
  await page
    .locator('.ag-header-cell[col-id="rawValue"] .ag-floating-filter-button button')
    .click();
  await page.getByRole('combobox', { name: 'Filtering operator' }).click();
  await page.getByRole('option', { name: 'Greater than', exact: true }).click();

  const bothColumns = page.waitForRequest((request) => {
    const filterModel = filterModelOf(request.url());
    return Boolean(filterModel.dasKey && filterModel.rawValue);
  });
  await page.getByRole('spinbutton', { name: 'Filter Value' }).fill('70');
  const request = await bothColumns;
  await page.keyboard.press('Escape');

  expect(Object.keys(filterModelOf(request.url())).sort()).toEqual(['dasKey', 'rawValue']);

  await expect(dasKeyCells(page).filter({ hasNotText: 'TEMP' })).toHaveCount(0);
  const rawValues = await loadedCells(page, 'rawValue').allInnerTexts();
  expect(rawValues.length).toBeGreaterThan(0);
  for (const rawValue of rawValues) {
    expect(Number(rawValue)).toBeGreaterThan(70);
  }
});
