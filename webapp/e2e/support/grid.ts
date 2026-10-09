import { expect, Locator, Page } from '@playwright/test';
import { isGridPage } from './responses';

/**
 * Waits for a page of the grid behind `path`, filtered to `filter` if given, and checks only its
 * status: Chromium may already have dropped the body of these grid requests.
 */
export async function expectGridPage(page: Page, path: string, filter?: string): Promise<void> {
  const response = await page.waitForResponse((candidate) => isGridPage(candidate, path, filter));
  expect(response.ok()).toBe(true);
}

/**
 * Fills the column filter `filterInput` with `value` and waits for the filtered page, so a missing
 * row can't pass on a request that never came back, and a click can't land on a row the reload is
 * about to replace.
 */
export async function filterGrid(
  page: Page,
  path: string,
  filterInput: string,
  value: string,
): Promise<void> {
  const filtered = expectGridPage(page, path, value);
  await page.getByRole('textbox', { name: filterInput }).fill(value);
  await filtered;
}

/** The cell holding `text`, matched exactly: the floating-filter cell holds the same text. */
export function gridCell(page: Page, text: string): Locator {
  return page.getByRole('gridcell', { name: text, exact: true });
}
