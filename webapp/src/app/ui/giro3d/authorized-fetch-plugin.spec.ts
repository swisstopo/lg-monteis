import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthorizedFetchPlugin } from './authorized-fetch-plugin';

const TILE_URL = 'http://example.com/tile.glb';

describe('AuthorizedFetchPlugin', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(new Response(null, { status: 200 }))),
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  const sentRequest = () => vi.mocked(fetch).mock.calls.at(-1)![0] as Request;

  it('reads the authorization for every single request', async () => {
    let authorization = 'Bearer first-token';
    const plugin = new AuthorizedFetchPlugin(() => authorization);

    await plugin.fetchData(TILE_URL);
    expect(sentRequest().headers.get('Authorization')).toBe('Bearer first-token');

    authorization = 'Bearer refreshed-token';
    await plugin.fetchData(TILE_URL);
    expect(sentRequest().headers.get('Authorization')).toBe('Bearer refreshed-token');
  });

  it('keeps the headers and options provided by the caller', async () => {
    const { signal } = new AbortController();

    await new AuthorizedFetchPlugin(() => 'Bearer first-token').fetchData(TILE_URL, {
      headers: { 'X-Test': 'kept' },
      signal,
    });

    expect(sentRequest().headers.get('X-Test')).toBe('kept');
    expect(sentRequest().headers.get('Authorization')).toBe('Bearer first-token');
  });

  it('sends no authorization header while there is no token', async () => {
    await new AuthorizedFetchPlugin(() => null).fetchData(TILE_URL);

    expect(sentRequest().headers.get('Authorization')).toBeNull();
  });
});
