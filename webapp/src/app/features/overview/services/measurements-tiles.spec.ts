import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { SKIP_GLOBAL_ERROR_TOAST } from '../../../core/http/http-context';
import { MeasurementsTiles, TILESET_URL_PREFIX } from './measurements-tiles';

const TILESET_URL = `${TILESET_URL_PREFIX}/some-tileset/tileset.json`;
const TILE_URL = `${TILESET_URL_PREFIX}/some-tileset/tile.glb`;

describe('MeasurementsTiles', () => {
  let service: MeasurementsTiles;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(MeasurementsTiles);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('hands back the tileset as a JSON-parsable response', async () => {
    const tileset = { asset: { version: '1.0' } };
    const pending = service.fetch(TILESET_URL);

    httpMock.expectOne(TILESET_URL).flush(new TextEncoder().encode(JSON.stringify(tileset)).buffer);

    const response = await pending;
    expect(response.ok).toBe(true);
    expect(response.status).toBe(200);
    expect(await response.json()).toEqual(tileset);
  });

  it('hands back tile contents as an array buffer', async () => {
    const contents = new Uint8Array([1, 2, 3]);
    const pending = service.fetch(TILE_URL);

    httpMock.expectOne(TILE_URL).flush(contents.buffer);

    expect(new Uint8Array(await (await pending).arrayBuffer())).toEqual(contents);
  });

  it('keeps the response headers', async () => {
    const pending = service.fetch(TILESET_URL);

    httpMock
      .expectOne(TILESET_URL)
      .flush(new ArrayBuffer(0), { headers: { 'Content-Type': 'application/json' } });

    expect((await pending).headers.get('Content-Type')).toBe('application/json');
  });

  it('never requests a URL outside the tileset prefix', async () => {
    await expect(service.fetch('https://evil.example.com/tileset.json')).rejects.toThrow(
      TILESET_URL_PREFIX,
    );

    httpMock.expectNone(() => true);
  });

  it('rejects when the request fails', async () => {
    const pending = service.fetch(TILE_URL);

    httpMock.expectOne(TILE_URL).flush(null, { status: 404, statusText: 'Not Found' });

    await expect(pending).rejects.toBeInstanceOf(HttpErrorResponse);
  });

  it('opts out of the global error toast, the 3D view reports errors itself', () => {
    void service.fetch(TILE_URL).catch(() => {});

    const request = httpMock.expectOne(TILE_URL);
    expect(request.request.context.get(SKIP_GLOBAL_ERROR_TOAST)).toBe(true);
    request.flush(new ArrayBuffer(0));
  });

  it('cancels the request when the signal is aborted', async () => {
    const controller = new AbortController();
    const pending = service.fetch(TILE_URL, { signal: controller.signal });
    const request = httpMock.expectOne(TILE_URL);

    controller.abort();

    expect(request.cancelled).toBe(true);
    await expect(pending).rejects.toThrow();
  });

  it('does not even start a request for an already aborted signal', async () => {
    const signal = AbortSignal.abort();

    await expect(service.fetch(TILE_URL, { signal })).rejects.toThrow();

    httpMock.expectNone(TILE_URL);
  });
});
