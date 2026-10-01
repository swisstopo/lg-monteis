import { createKeycloakAdminApi, ExperimentAccess, grantExperimentAccess } from './keycloak';
import { createMonteisApi, findExperimentIdByName } from './monteis-api';

/**
 * Creates an experiment through the API as admin-user and gives `username` `access` to it through
 * the experiment's Keycloak access subgroup. Returns the experiment's name and id.
 *
 * The name is `<greekLetter>-<random>`: experiment names are unique and these tests never delete
 * what they create, so every run and every browser project needs its own name. Each spec file
 * takes its own Greek letter.
 *
 * The user only gets the new access with their next login, which issues a fresh access token.
 */
export async function createExperimentWithAccess(
  greekLetter: string,
  username: string,
  access: ExperimentAccess,
): Promise<{ name: string; id: string }> {
  const adminApi = await createMonteisApi('admin-user', 'admin-user');
  const keycloakApi = await createKeycloakAdminApi();
  try {
    // Sliced so the name stays below the 50 character limit.
    const name = `${greekLetter}-${crypto.randomUUID().substring(0, 8)}`;
    const response = await adminApi.post('/api/experiments', {
      data: { name, period: { start: '2030-01-01', end: '2030-05-05' } },
    });
    if (!response.ok()) {
      throw new Error(
        `Creating experiment "${name}" failed: ${response.status()} ${await response.text()}`,
      );
    }

    const id = await findExperimentIdByName(adminApi, name);
    await grantExperimentAccess(keycloakApi, {
      experimentName: name,
      experimentId: id,
      username,
      access,
    });
    return { name, id };
  } finally {
    await adminApi.dispose();
    await keycloakApi.dispose();
  }
}
