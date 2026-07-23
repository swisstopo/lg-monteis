export interface RuntimeEnv {
  keycloakIssuer: string;
  keycloakClientId: string;
}

// Fetched once at app startup (see provide-auth.ts) from public/env.json. Its content is
// checked-in for local dev, and overridden at build time with public/e2e/env.json's content
// by angular.json's `e2e` build configuration for e2e testing.
export async function loadRuntimeEnv(): Promise<RuntimeEnv> {
  const response = await fetch('/env.json');
  return response.json();
}
