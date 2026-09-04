import { expect, test, type Response } from '@playwright/test';
import { loginAsAdmin } from '../support/login';

const TILESET_URL = 'http://localhost:4200/api/tilesets/monteis-octree-poc/tileset.json';

test.beforeEach(async ({ page }) => {
  await page.goto('http://localhost:4200/');
  await loginAsAdmin(page);

  await page.getByRole('button', { name: 'data_exploration' }).click();
});

test('loads the backend-served tileset with an authenticated request', async ({ page }) => {
  const tilesetResponse = page.waitForResponse(TILESET_URL);
  const tileResponse = page.waitForResponse((response: Response) =>
    response.url().endsWith('.glb'),
  );

  await page.getByRole('link', { name: '3D View' }).click();

  expect((await tilesetResponse).status()).toBe(200);
  expect((await tileResponse).status()).toBe(200);

  await expect(page.locator('app-giro3d canvas')).toBeAttached();
});
