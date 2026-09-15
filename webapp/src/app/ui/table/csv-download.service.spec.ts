import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CsvDownloadService } from './csv-download.service';

describe('CsvDownloadService', () => {
  let service: CsvDownloadService;
  let clickedAnchors: HTMLAnchorElement[];
  const realCreateElement = document.createElement.bind(document);

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(CsvDownloadService);

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
    vi.restoreAllMocks();
  });

  it('triggers a download of the given blob via a temporary anchor', () => {
    service.download(new Blob(['dasSensorAlias\r\nSENS-01\r\n']), 'sensors.csv');

    expect(clickedAnchors).toHaveLength(1);
    expect(clickedAnchors[0].download).toBe('sensors.csv');
    expect(clickedAnchors[0].href).toContain('blob:mock-url');
    expect(URL.createObjectURL).toHaveBeenCalledTimes(1);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock-url');
  });

  it('revokes the object URL even if the anchor click throws', () => {
    vi.spyOn(document, 'createElement').mockImplementation((tagName: string) => {
      const element = realCreateElement(tagName);
      if (tagName === 'a') {
        vi.spyOn(element as HTMLAnchorElement, 'click').mockImplementation(() => {
          throw new Error('boom');
        });
      }
      return element;
    });

    expect(() => service.download(new Blob(), 'sensors.csv')).toThrow('boom');
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock-url');
  });
});
