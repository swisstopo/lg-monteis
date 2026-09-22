import { APIRequestContext, request } from '@playwright/test';

// The e2e Keycloak is the Testcontainer started by the backend's e2e-test profile on the fixed
// port 18081 (KeycloakTestcontainersConfiguration), with the same admin credentials as the
// docker-compose Keycloak used for local dev.
const KEYCLOAK_URL = process.env['E2E_KEYCLOAK_URL'] ?? 'http://localhost:18081/auth';
const REALM = 'monteis';
const ADMIN_USERNAME = process.env['KC_ADMIN_USER'] ?? 'admin';
const ADMIN_PASSWORD = process.env['KC_ADMIN_PASSWORD'] ?? 'admin';

const ADMIN_API = `${KEYCLOAK_URL}/admin/realms/${REALM}`;

// Namespace group every per-experiment group is created under (docker/keycloak/realm/realm-base.json).
const EXPERIMENTS_GROUP = 'Experiments';
// Read access is granted per experiment through this client role on the per-experiment group.
const SPA_CLIENT_ID = 'monteis-spa';
const READ_ROLE = 'monteis-client:read';
// The group attribute the monteis-experiment-access protocol mapper aggregates into the
// experiment_ids access-token claim, which the backend turns into the RLS experiment ids.
const EXPERIMENT_IDS_ATTRIBUTE = 'experiment_ids';

/**
 * Logs into the master realm as the Keycloak admin and returns a request context that carries the
 * resulting bearer token. Dispose it when the test is done.
 */
export async function createKeycloakAdminApi(): Promise<APIRequestContext> {
  const tokenContext = await request.newContext();
  const response = await tokenContext.post(
    `${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token`,
    {
      form: {
        grant_type: 'password',
        client_id: 'admin-cli',
        username: ADMIN_USERNAME,
        password: ADMIN_PASSWORD,
      },
    },
  );
  const body = await readJson(response, 'Keycloak admin login');
  await tokenContext.dispose();

  return request.newContext({
    extraHTTPHeaders: { Authorization: `Bearer ${body.access_token}` },
  });
}

/**
 * Creates `Experiment <experimentName>` as a child of the Experiments group, scoped to
 * `experimentId` and granting read access, then makes `username` a member of it.
 *
 * Mirrors by hand what an admin does in the Keycloak console when a new experiment needs its own
 * access group; there is no MONTEIS API for it.
 */
export async function grantExperimentAccess(
  api: APIRequestContext,
  params: { experimentName: string; experimentId: string; username: string },
): Promise<void> {
  const parentGroupId = await findTopLevelGroupId(api, EXPERIMENTS_GROUP);
  const groupId = await createExperimentGroup(
    api,
    parentGroupId,
    `Experiment ${params.experimentName}`,
    params.experimentId,
  );
  await assignReadRole(api, groupId);
  await addUserToGroup(api, params.username, groupId);
}

async function findTopLevelGroupId(api: APIRequestContext, name: string): Promise<string> {
  const groups = await readJson(
    await api.get(`${ADMIN_API}/groups`, { params: { search: name } }),
    `lookup of group "${name}"`,
  );
  const group = groups.find((candidate: { name: string }) => candidate.name === name);
  if (!group) {
    throw new Error(`Keycloak group "${name}" not found in realm ${REALM}`);
  }
  return group.id;
}

async function createExperimentGroup(
  api: APIRequestContext,
  parentGroupId: string,
  name: string,
  experimentId: string,
): Promise<string> {
  const response = await api.post(`${ADMIN_API}/groups/${parentGroupId}/children`, {
    data: {
      name,
      attributes: { [EXPERIMENT_IDS_ATTRIBUTE]: [experimentId] },
    },
  });
  if (!response.ok()) {
    throw new Error(
      `Creating group "${name}" failed: ${response.status()} ${await response.text()}`,
    );
  }
  // Keycloak answers 201 with the new group's URL; its last segment is the group id.
  const location = response.headers()['location'];
  if (!location) {
    throw new Error(`Creating group "${name}" returned no Location header`);
  }
  return location.substring(location.lastIndexOf('/') + 1);
}

async function assignReadRole(api: APIRequestContext, groupId: string): Promise<void> {
  const clients = await readJson(
    await api.get(`${ADMIN_API}/clients`, { params: { clientId: SPA_CLIENT_ID } }),
    `lookup of client "${SPA_CLIENT_ID}"`,
  );
  if (clients.length === 0) {
    throw new Error(`Keycloak client "${SPA_CLIENT_ID}" not found in realm ${REALM}`);
  }
  const clientUuid = clients[0].id;

  const role = await readJson(
    await api.get(`${ADMIN_API}/clients/${clientUuid}/roles/${encodeURIComponent(READ_ROLE)}`),
    `lookup of client role "${READ_ROLE}"`,
  );

  const response = await api.post(
    `${ADMIN_API}/groups/${groupId}/role-mappings/clients/${clientUuid}`,
    {
      data: [role],
    },
  );
  if (!response.ok()) {
    throw new Error(
      `Assigning "${READ_ROLE}" failed: ${response.status()} ${await response.text()}`,
    );
  }
}

async function addUserToGroup(
  api: APIRequestContext,
  username: string,
  groupId: string,
): Promise<void> {
  const users = await readJson(
    await api.get(`${ADMIN_API}/users`, { params: { username, exact: true } }),
    `lookup of user "${username}"`,
  );
  if (users.length === 0) {
    throw new Error(`Keycloak user "${username}" not found in realm ${REALM}`);
  }

  const response = await api.put(`${ADMIN_API}/users/${users[0].id}/groups/${groupId}`);
  if (!response.ok()) {
    throw new Error(
      `Adding "${username}" to the group failed: ${response.status()} ${await response.text()}`,
    );
  }
}

async function readJson(
  response: Awaited<ReturnType<APIRequestContext['get']>>,
  what: string,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
): Promise<any> {
  if (!response.ok()) {
    throw new Error(`${what} failed: ${response.status()} ${await response.text()}`);
  }
  return response.json();
}
