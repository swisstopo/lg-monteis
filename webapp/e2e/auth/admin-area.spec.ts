import { expect, test } from '../support/fixtures';
import { label, openAppAs, SEED_USERS, SeedUser } from '../support/login';

/**
 * MON-199: the admin area is a side-bar menu that only a Monteis admin gets. The part is added
 * after `/api/me` answers, so the non-admin tests wait for that answer (openAppAs) before they
 * assert its absence.
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
