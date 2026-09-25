import { expect, type Page } from '@playwright/test';

export const scrollToTableColumn = async (page: Page, columnName: string) => {
  const header = page.getByRole('columnheader', { name: columnName, exact: true });
  const viewport = page.locator('.ag-body-horizontal-scroll-viewport');

  await expect(async () => {
    if (!(await header.isVisible())) {
      await viewport.evaluate((el) => el.scrollBy({ left: 500 }));
    }

    await expect(header).toBeVisible({ timeout: 150 });
  }).toPass({
    intervals: [250, 500, 1000],
    timeout: 15000,
  });

  await header.scrollIntoViewIfNeeded();
};
