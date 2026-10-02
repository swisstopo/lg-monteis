import { APIRequestContext, request } from '@playwright/test';
import { SeedUser } from './login';
import { Experiment } from './monteis-api';
import { readJson } from './responses';

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

/** The Keycloak group id of each access subgroup of one experiment. */
export type ExperimentAccessGroups = Record<ExperimentAccess, string>;

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
  const body = await readJson<{ access_token: string }>(response, 'Keycloak admin login');
  await tokenContext.dispose();

  return request.newContext({
    extraHTTPHeaders: { Authorization: `Bearer ${body.access_token}` },
  });
}

/**
 * Creates `/Experiments/Experiment <name>` with its two access subgroups `read` and
 * `read + write`, both scoped to the experiment's id, and returns the subgroup ids. Nobody is a
 * member yet; see {@link addUserToGroup}.
 *
 * Mirrors by hand what an admin does in the Keycloak console when a new experiment needs its own
 * access groups; there is no MONTEIS API for it.
 */
export async function createExperimentAccessGroups(
  api: APIRequestContext,
  experiment: Experiment,
): Promise<ExperimentAccessGroups> {
  const experimentsGroupId = await findTopLevelGroupId(api, EXPERIMENTS_GROUP);
  const experimentGroupId = await createChildGroup(
    api,
    experimentsGroupId,
    `Experiment ${experiment.name}`,
    {},
  );
  const clientUuid = await findClientUuid(api, SPA_CLIENT_ID);
  const subgroup = { experimentGroupId, clientUuid, experimentId: experiment.id };
  return {
    read: await createAccessSubgroup(api, { ...subgroup, access: 'read' }),
    'read + write': await createAccessSubgroup(api, { ...subgroup, access: 'read + write' }),
  };
}

/** Makes `user` a member of the group `groupId`. It takes effect with the user's next login. */
export async function addUserToGroup(
  api: APIRequestContext,
  user: SeedUser,
  groupId: string,
): Promise<void> {
  const userId = await findUserId(api, user.username);
  const response = await api.put(`${ADMIN_API}/users/${userId}/groups/${groupId}`);
  if (!response.ok()) {
    throw new Error(
      `Adding "${user.username}" to the group failed: ${response.status()} ${await response.text()}`,
    );
  }
}

/**
 * Creates the `access` subgroup of an experiment group: scoped to `experimentId` through the
 * access's group attribute and granting the access's client roles. Returns the subgroup id.
 */
async function createAccessSubgroup(
  api: APIRequestContext,
  subgroup: {
    experimentGroupId: string;
    clientUuid: string;
    experimentId: string;
    access: ExperimentAccess;
  },
): Promise<string> {
  const { attribute, roles } = ACCESS_SUBGROUPS[subgroup.access];
  const groupId = await createChildGroup(api, subgroup.experimentGroupId, subgroup.access, {
    [attribute]: [subgroup.experimentId],
  });
  await assignClientRoles(api, groupId, subgroup.clientUuid, roles);
  return groupId;
}

async function findTopLevelGroupId(api: APIRequestContext, name: string): Promise<string> {
  const groups = await readJson<{ id: string; name: string }[]>(
    await api.get(`${ADMIN_API}/groups`, { params: { search: name } }),
    `lookup of group "${name}"`,
  );
  const group = groups.find((candidate) => candidate.name === name);
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
  const location = response.headers()['location'];
  if (!location) {
    throw new Error(`Creating group "${name}" returned no Location header`);
  }
  return idFromLocation(location);
}

/** Keycloak answers a create with the new resource's URL; its last segment is the id. */
function idFromLocation(location: string): string {
  return location.substring(location.lastIndexOf('/') + 1);
}

async function findClientUuid(api: APIRequestContext, clientId: string): Promise<string> {
  const clients = await readJson<{ id: string }[]>(
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
  const roles = await Promise.all(
    roleNames.map((roleName) => findClientRole(api, clientUuid, roleName)),
  );
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

/** Returns the role representation Keycloak expects back when the role is assigned. */
async function findClientRole(
  api: APIRequestContext,
  clientUuid: string,
  roleName: string,
): Promise<unknown> {
  return readJson(
    await api.get(`${ADMIN_API}/clients/${clientUuid}/roles/${encodeURIComponent(roleName)}`),
    `lookup of client role "${roleName}"`,
  );
}

async function findUserId(api: APIRequestContext, username: string): Promise<string> {
  const users = await readJson<{ id: string }[]>(
    await api.get(`${ADMIN_API}/users`, { params: { username, exact: true } }),
    `lookup of user "${username}"`,
  );
  if (users.length === 0) {
    throw new Error(`Keycloak user "${username}" not found in realm ${REALM}`);
  }
  return users[0].id;
}
