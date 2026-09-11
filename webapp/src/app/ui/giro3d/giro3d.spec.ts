import { ComponentFixture, TestBed } from '@angular/core/testing';
import type Tiles3D from '@giro3d/giro3d/entities/Tiles3D.js';
import { provideTranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Giro3d } from './giro3d';
import { TilesFetch } from './tiles-fetch-plugin';

const TILESET_URL = 'http://example.com/tileset';
const OTHER_TILESET_URL = 'http://example.com/other-tileset';
const TILE_URL = 'http://example.com/tile.glb';

// Empty dummy tileset
const TILESET = {
  asset: { version: '1.0' },
  geometricError: 100,
  root: {
    boundingVolume: { box: [0, 0, 0, 10, 0, 0, 0, 10, 0, 0, 0, 10] },
    geometricError: 0,
    refine: 'ADD',
    children: [],
  },
};

// `3d-tiles-renderer` is only a transitive dependency, so derive its types instead of importing it.
type TilesRenderer = Tiles3D['tiles'];
type FetchDataPlugin = { fetchData?: (url: string, options: RequestInit) => Promise<Response> };
type PluginDispatch = {
  invokeOnePlugin(fn: (plugin: FetchDataPlugin) => unknown): Promise<Response>;
};

/**
 * Requests a tile the way `TilesRendererBase` does: with a *shallow* copy of the single,
 * long-lived `fetchOptions` object it keeps per tileset, plus the tile's abort signal.
 */
const requestTileLikeTheRendererDoes = (
  tiles: TilesRenderer,
  url: string,
  signal: AbortSignal,
): Promise<Response> =>
  (tiles as unknown as PluginDispatch).invokeOnePlugin((plugin) =>
    plugin.fetchData?.(url, { ...tiles.fetchOptions, signal }),
  );

describe('Giro3d', () => {
  let component: Giro3d;
  let fixture: ComponentFixture<Giro3d>;
  let fetchTiles: ReturnType<typeof vi.fn<TilesFetch>>;

  const fetchedUrls = () => fetchTiles.mock.calls.map(([url]) => url);

  beforeEach(async () => {
    fetchTiles = vi.fn<TilesFetch>((url) => {
      if (url === TILESET_URL || url === OTHER_TILESET_URL) {
        return Promise.resolve(
          new Response(JSON.stringify(TILESET), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }),
        );
      }
      if (url === TILE_URL) {
        return Promise.resolve(new Response(new ArrayBuffer(0), { status: 200 }));
      }
      return Promise.reject(new Error(`Not Found: ${url}`));
    });

    // Nothing may bypass the injected fetch function, so make a direct fetch fail loudly.
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.reject(new Error('unexpected direct fetch'))),
    );

    await TestBed.configureTestingModule({
      imports: [Giro3d],
      providers: [provideTranslateService()],
    }).compileComponents();

    fixture = TestBed.createComponent(Giro3d);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('tilesetUrl', TILESET_URL);
    fixture.componentRef.setInput('sensors', []);
    fixture.componentRef.setInput('fetch', fetchTiles);
    await fixture.whenStable();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders the giro3d canvas into the view container', () => {
    const view: HTMLDivElement = fixture.nativeElement.querySelector('.view');
    expect(view.querySelector('canvas')).toBeTruthy();
  });

  it('loads the tileset from the given URL', () => {
    expect(fetchedUrls()).toContain(TILESET_URL);
  });

  it('loads the new tileset when the URL changes', async () => {
    expect(fetchedUrls()).not.toContain(OTHER_TILESET_URL);

    fixture.componentRef.setInput('tilesetUrl', OTHER_TILESET_URL);
    await fixture.whenStable();

    expect(fetchedUrls()).toContain(OTHER_TILESET_URL);
  });

  describe('tile requests', () => {
    const renderer = () => (component as unknown as { tileset: () => Tiles3D }).tileset().tiles;

    it('routes them through the injected fetch function, never through `fetch` itself', async () => {
      const { signal } = new AbortController();

      await requestTileLikeTheRendererDoes(renderer(), TILE_URL, signal);

      expect(fetchedUrls()).toContain(TILE_URL);
      expect(fetch).not.toHaveBeenCalled();
    });

    it('forwards the abort signal so discarded tiles stop downloading', async () => {
      const { signal } = new AbortController();

      await requestTileLikeTheRendererDoes(renderer(), TILE_URL, signal);

      expect(fetchTiles).toHaveBeenLastCalledWith(TILE_URL, { signal });
    });

    it('picks up a replaced fetch function without rebuilding the tileset', async () => {
      const tileset = renderer();
      const replacement = vi.fn<TilesFetch>(() =>
        Promise.resolve(new Response(new ArrayBuffer(0), { status: 200 })),
      );
      fixture.componentRef.setInput('fetch', replacement);
      await fixture.whenStable();

      const { signal } = new AbortController();
      await requestTileLikeTheRendererDoes(renderer(), TILE_URL, signal);

      expect(renderer()).toBe(tileset);
      expect(replacement).toHaveBeenCalledWith(TILE_URL, { signal });
      expect(fetchedUrls()).not.toContain(TILE_URL);
    });
  });
});
