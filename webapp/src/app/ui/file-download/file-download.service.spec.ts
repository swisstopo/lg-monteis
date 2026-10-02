import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { FileDownloadService } from './file-download.service';

describe('FileDownloadService', () => {
  let service: FileDownloadService;
  let clickedAnchors: HTMLAnchorElement[];
  const realCreateElement = document.createElement.bind(document);

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(FileDownloadService);

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

  describe('openInNewTab', () => {
    let tab: { location: { href: string }; close: ReturnType<typeof vi.fn> };

    beforeEach(() => {
      vi.useFakeTimers();
      tab = { location: { href: '' }, close: vi.fn() };
      vi.spyOn(window, 'open').mockReturnValue(tab as unknown as Window);
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it('opens the tab before loading and points it at the blob', async () => {
      const load = vi.fn(() => {
        expect(window.open).toHaveBeenCalledWith('', '_blank');
        return Promise.resolve(new Blob(['%PDF']));
      });

      await service.openInNewTab(load);

      expect(tab.location.href).toBe('blob:mock-url');
      expect(URL.revokeObjectURL).not.toHaveBeenCalled();
      vi.runAllTimers();
      expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock-url');
    });

    it('closes the tab and rethrows when loading fails', async () => {
      await expect(service.openInNewTab(() => Promise.reject(new Error('boom')))).rejects.toThrow(
        'boom',
      );

      expect(tab.close).toHaveBeenCalled();
      expect(URL.createObjectURL).not.toHaveBeenCalled();
    });
  });
});
