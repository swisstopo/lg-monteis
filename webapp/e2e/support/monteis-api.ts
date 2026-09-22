import { APIRequestContext, request } from '@playwright/test';

// The backend the Angular dev server proxies /api to (webapp/proxy.conf.json).
const API_URL = process.env['E2E_API_URL'] ?? 'http://localhost:8080';
const KEYCLOAK_URL = process.env['E2E_KEYCLOAK_URL'] ?? 'http://localhost:18081/auth';
const REALM = 'monteis';
const SPA_CLIENT_ID = 'monteis-spa';

export interface Experiment {
  id: string;
  name: string;
}

/**
 * Returns a request context authenticated as `username` through Keycloak's direct access grant
 * (enabled on monteis-spa), for the few things a test needs from the API rather than the UI -
 * namely an experiment's generated id. Dispose it when the test is done.
 */
export async function createMonteisApi(
  username: string,
  password: string,
): Promise<APIRequestContext> {
  const tokenContext = await request.newContext();
  const response = await tokenContext.post(
    `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`,
    {
      form: {
        grant_type: 'password',
        client_id: SPA_CLIENT_ID,
        username,
        password,
      },
    },
  );
  if (!response.ok()) {
    throw new Error(`Login as "${username}" failed: ${response.status()} ${await response.text()}`);
  }
  const { access_token } = await response.json();
  await tokenContext.dispose();

  return request.newContext({
    baseURL: API_URL,
    extraHTTPHeaders: { Authorization: `Bearer ${access_token}` },
  });
}

async function listExperiments(api: APIRequestContext): Promise<Experiment[]> {
  const response = await api.get('/api/experiments/all');
  if (!response.ok()) {
    throw new Error(`Listing experiments failed: ${response.status()} ${await response.text()}`);
  }
  return response.json();
}

export async function findExperimentIdByName(
  api: APIRequestContext,
  name: string,
): Promise<string> {
  const experiment = (await listExperiments(api)).find((candidate) => candidate.name === name);
  if (!experiment) {
    throw new Error(`Experiment "${name}" not found`);
  }
  return experiment.id;
}
