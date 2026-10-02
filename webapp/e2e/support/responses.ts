import { APIResponse, Response } from '@playwright/test';

/** Whether the browser `response` answers a request to `path`, whatever its host and query. */
export function hasPath(response: Response, path: string): boolean {
  return new URL(response.url()).pathname === path;
}

/** Returns the JSON body of `response`, or throws with its status and body when it failed. */
export async function readJson<T>(response: APIResponse, what: string): Promise<T> {
  if (!response.ok()) {
    throw new Error(`${what} failed: ${response.status()} ${await response.text()}`);
  }
  return response.json();
}
