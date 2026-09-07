import Fetcher, { type FetchOptions } from '@giro3d/giro3d/utils/Fetcher.js';

/**
 * Returns the `Authorization` header value to use, or `null` for an unauthenticated request.
 */
export type AuthorizationProvider = () => string | null;

/**
 * Replaces Giro3d's own `FetchPlugin` to add an `Authorization` header to every
 * 3D Tiles request.
 */
export class AuthorizedFetchPlugin {
  readonly name = 'AUTHORIZED_FETCH_PLUGIN';

  constructor(private readonly authorization: AuthorizationProvider) {}

  fetchData(url: RequestInfo | URL, options?: FetchOptions): Promise<Response> {
    const authorization = this.authorization();
    if (!authorization) {
      return Fetcher.fetch(url, options);
    }
    return Fetcher.fetch(url, {
      ...options,
      headers: { ...options?.headers, Authorization: authorization },
    });
  }
}
