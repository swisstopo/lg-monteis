export type TilesFetch = (
  url: string,
  options?: { signal?: AbortSignal | null },
) => Promise<Response>;

/**
 * Replaces Giro3d's own `FetchPlugin` so that 3D Tiles requests are made by the app instead of by
 * Giro3d's `Fetcher` – that way they go through the regular HTTP stack, interceptors included.
 *
 * Requires `enableFetchPlugin: false` on the entity, since the renderer dispatches `fetchData` to
 * the first plugin that handles it and Giro3d registers its own before this one.
 */
export class TilesFetchPlugin {
  readonly name = 'TILES_FETCH_PLUGIN';

  constructor(private readonly fetchTiles: TilesFetch) {}

  fetchData(url: string, options?: RequestInit): Promise<Response> {
    return this.fetchTiles(url, { signal: options?.signal });
  }
}
