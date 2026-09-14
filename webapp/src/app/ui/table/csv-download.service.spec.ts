import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SKIP_GLOBAL_ERROR_TOAST } from '@core/http/http-context';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CsvDownloadService } from './csv-download.service';

describe('CsvDownloadService', () => {
  let service: CsvDownloadService;
  let httpMock: HttpTestingController;
  let clickedAnchors: HTMLAnchorElement[];
  const realCreateElement = document.createElement.bind(document);

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CsvDownloadService);
    httpMock = TestBed.inject(HttpTestingController);

    clickedAnchors = [];
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock-url');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
    vi.spyOn(document, 'createElement').mockImplementation((tagName: string) => {
      const element = realCreateElement(tagName);
      if (tagName === 'a') {
        vi.spyOn(element as HTMLAnchorElement, 'click').mockImplementation(() => {
          clickedAnchors.push(element as HTMLAnchorElement);
        });
      }
      return element;
    });
  });

  afterEach(() => {
    httpMock.verify();
    vi.restoreAllMocks();
  });

  it('requests a blob response with no query params when none are given', () => {
    void service.download('/api/sensors/csv', 'sensors.csv');

    const request = httpMock.expectOne('/api/sensors/csv');
    expect(request.request.responseType).toBe('blob');
    expect(request.request.params.keys()).toEqual([]);
    request.flush(new Blob(['a,b\r\n1,2\r\n']));
  });

  it('appends sortModel/filterModel as query params when given', () => {
    void service.download('/api/sensors/csv', 'sensors.csv', {
      sortModel: '[{"colId":"name","sort":"asc"}]',
      filterModel: '{"name":{"filterType":"text","type":"contains","filter":"abc"}}',
    });

    const request = httpMock.expectOne(
      (req) =>
        req.url === '/api/sensors/csv' &&
        req.params.get('sortModel') === '[{"colId":"name","sort":"asc"}]' &&
        req.params.get('filterModel') ===
          '{"name":{"filterType":"text","type":"contains","filter":"abc"}}',
    );
    request.flush(new Blob(['a,b\r\n1,2\r\n']));
  });

  it('opts out of the global error toast - callers render their own download error', () => {
    void service.download('/api/sensors/csv', 'sensors.csv').catch(() => {});

    const request = httpMock.expectOne('/api/sensors/csv');
    expect(request.request.context.get(SKIP_GLOBAL_ERROR_TOAST)).toBe(true);
    request.flush(new Blob());
  });

  it('triggers a download of the response body via a temporary anchor', async () => {
    const pending = service.download('/api/sensors/csv', 'sensors.csv');

    httpMock.expectOne('/api/sensors/csv').flush(new Blob(['dasSensorAlias\r\nSENS-01\r\n']));
    await pending;

    expect(clickedAnchors).toHaveLength(1);
    expect(clickedAnchors[0].download).toBe('sensors.csv');
    expect(clickedAnchors[0].href).toContain('blob:mock-url');
    expect(URL.createObjectURL).toHaveBeenCalledTimes(1);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock-url');
  });

  it('rejects without downloading when the response is not ok', async () => {
    const pending = service.download('/api/sensors/csv', 'sensors.csv');

    httpMock.expectOne('/api/sensors/csv').flush(null, { status: 500, statusText: 'Error' });

    await expect(pending).rejects.toBeTruthy();
    expect(clickedAnchors).toHaveLength(0);
  });
});
