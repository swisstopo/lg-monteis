import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Giro3d } from './giro3d';

const TILESET_URL = 'http://example.com/tileset';
const OTHER_TILESET_URL = 'http://example.com/other-tileset';

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

describe('Giro3d', () => {
  let component: Giro3d;
  let fixture: ComponentFixture<Giro3d>;

  const toUrl = (input: RequestInfo | URL) =>
    input instanceof Request ? input.url : input.toString();

  const fetchedUrls = () => vi.mocked(fetch).mock.calls.map(([input]) => toUrl(input));

  beforeEach(async () => {
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
        return Promise.resolve(new Response(null, { status: 404, statusText: 'Not Found' }));
      }),
    );

    await TestBed.configureTestingModule({
      imports: [Giro3d],
    }).compileComponents();

    fixture = TestBed.createComponent(Giro3d);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('tilesetUrl', TILESET_URL);
    await fixture.whenStable();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders the giro3d canvas into the view container', () => {
    const view: HTMLDivElement = fixture.nativeElement.querySelector('div');
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
});
