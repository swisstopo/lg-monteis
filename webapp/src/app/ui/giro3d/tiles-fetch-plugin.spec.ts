import { describe, expect, it, vi } from 'vitest';
import { TilesFetch, TilesFetchPlugin } from './tiles-fetch-plugin';

const TILE_URL = 'http://example.com/tile.glb';

describe('TilesFetchPlugin', () => {
  const setup = () => {
    const response = new Response(new ArrayBuffer(0), { status: 200 });
    const fetchTiles = vi.fn<TilesFetch>(() => Promise.resolve(response));
    return { response, fetchTiles, plugin: new TilesFetchPlugin(fetchTiles) };
  };

  it('delegates to the given fetch function', async () => {
    const { response, fetchTiles, plugin } = setup();

    await expect(plugin.fetchData(TILE_URL)).resolves.toBe(response);
    expect(fetchTiles).toHaveBeenCalledWith(TILE_URL, { signal: undefined });
  });

  it('forwards the abort signal of the request', async () => {
    const { fetchTiles, plugin } = setup();
    const { signal } = new AbortController();

    await plugin.fetchData(TILE_URL, { signal });

    expect(fetchTiles).toHaveBeenCalledWith(TILE_URL, { signal });
  });

  it('drops the renderer-wide fetch options, headers are the HTTP stack’s business', async () => {
    const { fetchTiles, plugin } = setup();

    await plugin.fetchData(TILE_URL, { headers: { Authorization: 'Bearer stale-token' } });

    expect(fetchTiles).toHaveBeenCalledWith(TILE_URL, { signal: undefined });
  });
});
