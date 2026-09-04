import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { WorkbenchView } from '@scion/workbench';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import MeasurementsVisualization from './measurements-visualization';

// Empty dummy tileset, so the view does not reach out to the backend under test.
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

describe('MeasurementsVisualization', () => {
  let component: MeasurementsVisualization;
  let fixture: ComponentFixture<MeasurementsVisualization>;

  const toUrl = (input: RequestInfo | URL) =>
    input instanceof Request ? input.url : input.toString();

  const fetchedUrls = () => vi.mocked(fetch).mock.calls.map(([input]) => toUrl(input));

  beforeEach(async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        Promise.resolve(
          new Response(JSON.stringify(TILESET), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }),
        ),
      ),
    );

    await TestBed.configureTestingModule({
      imports: [MeasurementsVisualization],
      providers: [WorkbenchView, provideTranslateService()],
    }).compileComponents();

    fixture = TestBed.createComponent(MeasurementsVisualization);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('loads the tileset served by the backend', () => {
    expect(fetchedUrls()).toContain(`${window.location.origin}/api/tilesets/example/tileset.json`);
  });
});
