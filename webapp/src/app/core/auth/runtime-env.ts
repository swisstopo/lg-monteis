import { ENV_JSON_URL } from './env-url';

export interface RuntimeEnv {
  keycloakIssuer: string;
  keycloakClientId: string;
}

// Fetched once at app startup (see provide-auth.ts) from one of two checked-in, hardcoded JSON
// files (public/env.json for local dev, public/env.e2e.json for e2e), selected via
// angular.json's `e2e` build configuration.
export async function loadRuntimeEnv(): Promise<RuntimeEnv> {
  const response = await fetch(ENV_JSON_URL);
  return response.json();
}
