import { type Page } from '@playwright/test';

export const scrollToTableColumn = async (page: Page, columnName: string) => {
  const header = page.getByRole('columnheader', { name: columnName, exact: true });
  const viewport = page.locator('.ag-body-horizontal-scroll-viewport');

  while (!(await header.isVisible())) {
    await viewport.evaluate((el) => el.scrollBy({ left: 500 }));
    await page.waitForTimeout(100);
  }

  await header.scrollIntoViewIfNeeded();
};
