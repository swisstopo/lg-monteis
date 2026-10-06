import { APIRequestContext, test as base } from '@playwright/test';
import { createKeycloakAdminApi } from './keycloak';
import { SEED_USERS } from './login';
import { createMonteisApi } from './monteis-api';

export { expect } from '@playwright/test';

/**
 * Playwright's `test` plus two request contexts. A test gets one by naming it in its arguments; it
 * is created for that test only and disposed after it.
 * - `adminApi`: the MONTEIS API as admin-user.
 * - `keycloakApi`: the Keycloak admin API.
 */
export const test = base.extend<{ adminApi: APIRequestContext; keycloakApi: APIRequestContext }>({
  adminApi: async ({}, use) => {
    const api = await createMonteisApi(SEED_USERS.admin.username, SEED_USERS.admin.password);
    await use(api);
    await api.dispose();
  },
  keycloakApi: async ({}, use) => {
    const api = await createKeycloakAdminApi();
    await use(api);
    await api.dispose();
  },
});
