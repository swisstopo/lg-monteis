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
// Per-experiment access is granted through client roles of this client on the access subgroups.
const SPA_CLIENT_ID = 'monteis-spa';
const EXPERIMENT_READ_ROLE = 'monteis-client:experiment:read';
const EXPERIMENT_WRITE_ROLE = 'monteis-client:experiment:write';

/** The two access levels every experiment group offers, as subgroups (contract C1). */
export type ExperimentAccess = 'read' | 'read + write';

// Per access subgroup: the group attribute the monteis-experiment-access protocol mapper
// aggregates into the matching access-token claim (read_experiment_ids / write_experiment_ids),
// and the client roles the subgroup grants. Mirrors docker/keycloak/realm/patch.local.json.
const ACCESS_SUBGROUPS: Record<ExperimentAccess, { attribute: string; roles: string[] }> = {
  read: {
    attribute: 'read_experiment_ids',
    roles: [EXPERIMENT_READ_ROLE],
  },
  'read + write': {
    attribute: 'write_experiment_ids',
    roles: [EXPERIMENT_READ_ROLE, EXPERIMENT_WRITE_ROLE],
  },
};

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
 * Creates `/Experiments/Experiment <experimentName>` with its two access subgroups `read` and
 * `read + write`, both scoped to `experimentId`, then makes `username` a member of the subgroup
 * for the requested `access`.
 *
 * Mirrors by hand what an admin does in the Keycloak console when a new experiment needs its own
 * access groups; there is no MONTEIS API for it.
 */
export async function grantExperimentAccess(
  api: APIRequestContext,
  params: {
    experimentName: string;
    experimentId: string;
    username: string;
    access: ExperimentAccess;
  },
): Promise<void> {
  const experimentsGroupId = await findTopLevelGroupId(api, EXPERIMENTS_GROUP);
  const experimentGroupId = await createChildGroup(
    api,
    experimentsGroupId,
    `Experiment ${params.experimentName}`,
    {},
  );
  const spaClientUuid = await findClientUuid(api, SPA_CLIENT_ID);

  const subgroupIds = {} as Record<ExperimentAccess, string>;
  for (const access of Object.keys(ACCESS_SUBGROUPS) as ExperimentAccess[]) {
    const { attribute, roles } = ACCESS_SUBGROUPS[access];
    subgroupIds[access] = await createChildGroup(api, experimentGroupId, access, {
      [attribute]: [params.experimentId],
    });
    await assignClientRoles(api, subgroupIds[access], spaClientUuid, roles);
  }

  await addUserToGroup(api, params.username, subgroupIds[params.access]);
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

async function createChildGroup(
  api: APIRequestContext,
  parentGroupId: string,
  name: string,
  attributes: Record<string, string[]>,
): Promise<string> {
  const response = await api.post(`${ADMIN_API}/groups/${parentGroupId}/children`, {
    data: { name, attributes },
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

async function findClientUuid(api: APIRequestContext, clientId: string): Promise<string> {
  const clients = await readJson(
    await api.get(`${ADMIN_API}/clients`, { params: { clientId } }),
    `lookup of client "${clientId}"`,
  );
  if (clients.length === 0) {
    throw new Error(`Keycloak client "${clientId}" not found in realm ${REALM}`);
  }
  return clients[0].id;
}

async function assignClientRoles(
  api: APIRequestContext,
  groupId: string,
  clientUuid: string,
  roleNames: string[],
): Promise<void> {
  const roles = [];
  for (const roleName of roleNames) {
    roles.push(
      await readJson(
        await api.get(`${ADMIN_API}/clients/${clientUuid}/roles/${encodeURIComponent(roleName)}`),
        `lookup of client role "${roleName}"`,
      ),
    );
  }

  const response = await api.post(
    `${ADMIN_API}/groups/${groupId}/role-mappings/clients/${clientUuid}`,
    { data: roles },
  );
  if (!response.ok()) {
    throw new Error(
      `Assigning ${roleNames.join(', ')} failed: ${response.status()} ${await response.text()}`,
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
