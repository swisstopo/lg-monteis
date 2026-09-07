import { ComponentFixture, TestBed } from '@angular/core/testing';
import type Tiles3D from '@giro3d/giro3d/entities/Tiles3D.js';
import { provideTranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Giro3d } from './giro3d';

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
 * long-lived `fetchOptions` object it keeps per tileset. That shared object is the point of these
 * tests — whoever writes an Authorization header into it freezes the token for the whole tileset.
 */
const requestTileLikeTheRendererDoes = (tiles: TilesRenderer, url: string): Promise<Response> => {
  const { signal } = new AbortController();
  return (tiles as unknown as PluginDispatch).invokeOnePlugin((plugin) =>
    plugin.fetchData?.(url, { ...tiles.fetchOptions, signal }),
  );
};

describe('Giro3d', () => {
  let component: Giro3d;
  let fixture: ComponentFixture<Giro3d>;
  let accessToken: string;

  const toUrl = (input: RequestInfo | URL) =>
    input instanceof Request ? input.url : input.toString();

  const fetchedUrls = () => vi.mocked(fetch).mock.calls.map(([input]) => toUrl(input));

  beforeEach(async () => {
    accessToken = 'first-token';

    vi.stubGlobal(
      'fetch',
      vi.fn((input: RequestInfo | URL) => {
        const url = toUrl(input);
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
        return Promise.resolve(new Response(null, { status: 404, statusText: 'Not Found' }));
      }),
    );

    await TestBed.configureTestingModule({
      imports: [Giro3d],
      providers: [provideTranslateService()],
    }).compileComponents();

    fixture = TestBed.createComponent(Giro3d);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('tilesetUrl', TILESET_URL);
    fixture.componentRef.setInput('authorization', () => `Bearer ${accessToken}`);
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

  describe('authorization', () => {
    const requestAt = (index: number) => vi.mocked(fetch).mock.calls.at(index)![0] as Request;

    const renderer = () => (component as unknown as { tileset: () => Tiles3D }).tileset().tiles;

    it('sends the current token with the root tileset request', () => {
      expect(requestAt(0).url).toBe(TILESET_URL);
      expect(requestAt(0).headers.get('Authorization')).toBe('Bearer first-token');
    });

    it('sends the refreshed token with tiles requested after a token refresh', async () => {
      accessToken = 'refreshed-token';

      await requestTileLikeTheRendererDoes(renderer(), TILE_URL);

      expect(requestAt(-1).url).toBe(TILE_URL);
      expect(requestAt(-1).headers.get('Authorization')).toBe('Bearer refreshed-token');
    });

    it('never caches the token on the renderer-wide fetch options', () => {
      // The root load must not write anything into the object that every later tile request
      // shallow-copies.
      expect(renderer().fetchOptions.headers).toBeUndefined();
    });
  });
});
