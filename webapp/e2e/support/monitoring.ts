import { APIRequestContext, expect, Locator, Page } from '@playwright/test';
import { readJson } from './responses';

/**
 * Opens the notification page of `experimentId` by deep link, the way a bookmarked tab opens it.
 */
export async function openNotificationPage(page: Page, experimentId: string): Promise<void> {
  // scion reads views from main_area param, a bare /monitoring/<id> opens no tab:
  // take a real tab url and point its outlet at the page
  await page.getByTitle('Measurements').click();
  await page.getByRole('link', { name: 'Table' }).click();
  await expect(page).toHaveURL(/:measurements-table\)/);
  const deepLink = page.url().replace(':measurements-table)', `:monitoring/${experimentId})`);
  await page.goto(deepLink);
}

/** The workbench tab titled `title`. */
export function viewTab(page: Page, title: string): Locator {
  return page.locator('wb-view-tab', { hasText: title });
}

export function recipientsSection(page: Page): Locator {
  return page.getByTestId('recipients');
}

export function recipientChips(page: Page): Locator {
  return recipientsSection(page).locator('mat-chip');
}

/** Creates a sensor of `experimentId` without DAS sensor and parameter alias, so without DAS key. */
export async function createSensorWithoutDasKey(
  api: APIRequestContext,
  experimentId: string,
  name: string,
): Promise<void> {
  const response = await api.post('/api/sensors', {
    data: {
      name,
      das: 'SOL_EXPERTS',
      mainExperimentId: experimentId,
      coordinates: { x: 1, y: 2, z: 3 },
      active: true,
      parameters: [
        {
          name: 'No DAS key',
          unit: 'METER',
          type: { name: 'Temperature' },
          alarmLimits: { lower: 0, upper: 1 },
          active: true,
        },
      ],
    },
  });
  await readJson(response, `Creating sensor "${name}"`);
}
