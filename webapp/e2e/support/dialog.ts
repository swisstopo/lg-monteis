import { expect, Locator } from '@playwright/test';

/**
 * Waits until `dialog` has autofocused its first field. It does so once its open animation ends,
 * a fill still running then types into that field instead of the one it was meant for.
 */
export async function waitForAutofocus(dialog: Locator): Promise<void> {
  await expect(dialog.locator(':focus')).toHaveCount(1);
}

/**
 * Types `text` into the autocomplete `input` and picks `option` by keyboard. The CDK overlay is
 * position: fixed and can render outside the viewport (WebKit gives it a zero-overlap bounding
 * box), so no click has coordinates to land on; typing narrows the panel to the option and Enter
 * picks it wherever the panel is. Waits for the panel to close, WebKit lets an open one swallow
 * the next click.
 */
export async function pickAutocompleteOption(
  input: Locator,
  text: string,
  option: string = text,
): Promise<void> {
  const optionLocator = input.page().getByRole('option', { name: option, exact: true });
  await input.fill(text);
  await expect(optionLocator).toBeVisible();
  await input.press('ArrowDown');
  await input.press('Enter');
  await expect(optionLocator).toBeHidden();
}
