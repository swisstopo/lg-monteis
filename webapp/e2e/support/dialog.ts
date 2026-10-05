import { expect, Locator } from '@playwright/test';

/**
 * Waits until `dialog` has autofocused its first field. It does so once its open animation ends,
 * a fill still running then types into that field instead of the one it was meant for.
 */
export async function waitForAutofocus(dialog: Locator): Promise<void> {
  await expect(dialog.locator(':focus')).toHaveCount(1);
}
