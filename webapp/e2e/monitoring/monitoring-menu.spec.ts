import { SEEDED_EXPERIMENTS } from '../support/experiments';
import { expect, test } from '../support/fixtures';
import { label, openAppAs, SEED_USERS } from '../support/login';
import { openNotificationsMenu, viewTab } from '../support/monitoring';

function notificationsOf(experimentName: string): string {
  return `Notifications – ${experimentName}`;
}

// other specs grant alice write on fresh experiments, so only the seeded ones are asserted
test(`${label(SEED_USERS.alice)} sees only the experiments they may write and opens one`, async ({
  page,
}) => {
  await openAppAs(page, SEED_USERS.alice);

  const entries = await openNotificationsMenu(page);

  await expect(entries.filter({ hasText: SEEDED_EXPERIMENTS.alpha })).toHaveCount(1);
  await expect(entries.filter({ hasText: SEEDED_EXPERIMENTS.beta })).toHaveCount(0);
  await expect(entries.filter({ hasText: SEEDED_EXPERIMENTS.gamma })).toHaveCount(0);

  await entries.filter({ hasText: notificationsOf(SEEDED_EXPERIMENTS.alpha) }).click();
  await expect(viewTab(page, `Notifications ${SEEDED_EXPERIMENTS.alpha}`)).toBeVisible();
});

test(`${label(SEED_USERS.admin)} sees every experiment`, async ({ page }) => {
  await openAppAs(page, SEED_USERS.admin);

  const entries = await openNotificationsMenu(page);

  for (const name of Object.values(SEEDED_EXPERIMENTS)) {
    await expect(entries.filter({ hasText: notificationsOf(name) })).toHaveCount(1);
  }
});
