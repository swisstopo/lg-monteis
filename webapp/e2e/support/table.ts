import { expect, Locator, Page } from '@playwright/test';

/**
 * Waits for the table's Download button. Every caller gets it, so it shows the table is there even
 * when the write actions are not.
 */
export async function expectTableToolbar(page: Page): Promise<void> {
  await expect(page.getByRole('button', { name: 'Download' })).toBeVisible();
}

/**
 * The grid's data rows, found by their selection checkboxes. The header and floating-filter rows
 * contain gridcells too, so a count of gridcells would never reach zero.
 */
export function dataRows(page: Page): Locator {
  return page.getByRole('checkbox', { name: /toggle row selection/ });
}
