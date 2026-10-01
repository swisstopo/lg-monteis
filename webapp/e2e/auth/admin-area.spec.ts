import { expect, test } from '../support/fixtures';
import { label, login, openAppAs, SEED_USERS, SeedUser } from '../support/login';
import { hasPath } from '../support/responses';

/**
 * MON-199: the admin area is a side-bar menu that only a Monteis admin gets. The workbench picks
 * the admin or the default perspective from `/api/me`, so the side bar is final at first render.
 */

const NON_ADMINS: SeedUser[] = [SEED_USERS.alice, SEED_USERS.bob, SEED_USERS.basisUser];

test(`${label(SEED_USERS.admin)} opens the admin menu and its Organisations entry`, async ({
  page,
}) => {
  await openAppAs(page, SEED_USERS.admin);

  await page.getByTitle('Admin').click();
  await page.getByRole('link', { name: 'Organisations' }).click();

  await expect(page.getByText('Organisation management follows in a later release.')).toBeVisible();
});

for (const user of NON_ADMINS) {
  test(`${label(user)} does not see the admin menu`, async ({ page }) => {
    await openAppAs(page, user);

    await expect(page.getByTitle('Setup')).toBeVisible();
    await expect(page.getByTitle('Admin')).toHaveCount(0);
  });
}

// The workbench persists the layout in the browser's local storage, which survives a logout.
test(`${label(SEED_USERS.basisUser)} logging in after an admin on the same browser does not see the admin menu`, async ({
  page,
}) => {
  await openAppAs(page, SEED_USERS.admin);
  await expect(page.getByTitle('Admin')).toBeVisible();

  await page.getByRole('button', { name: 'Logout' }).click();
  const currentUser = page.waitForResponse((response) => hasPath(response, '/api/me'));
  await login(page, SEED_USERS.basisUser);
  expect((await currentUser).ok()).toBe(true);

  await expect(page.getByTitle('Setup')).toBeVisible();
  await expect(page.getByTitle('Admin')).toHaveCount(0);
});
