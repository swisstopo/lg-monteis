import { HttpContext } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { FulcrumDocumentControllerService } from '@core/generated';
import { SKIP_GLOBAL_ERROR_TOAST } from '@core/http/http-context';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { FulcrumDocumentService } from './fulcrum-document.service';

const ENTRY = { id: 'entry-1', title: 'Geology of Mont Terri' };
const RUNNING = { running: true, lastSyncedAt: '2026-10-15T08:00:00Z' };

describe('FulcrumDocumentService', () => {
  let api: Record<string, ReturnType<typeof vi.fn>>;
  let service: FulcrumDocumentService;

  beforeEach(() => {
    api = {
      getFulcrumEntries: vi.fn(() => of([ENTRY])),
      downloadFulcrumDocument: vi.fn(() => of(new Blob(['%PDF']))),
      getFulcrumSyncStatus: vi.fn(() => of(RUNNING)),
      startFulcrumSync: vi.fn(() => of(RUNNING)),
    };
    TestBed.configureTestingModule({
      providers: [{ provide: FulcrumDocumentControllerService, useValue: api }],
    });
    service = TestBed.inject(FulcrumDocumentService);
  });

  it('lists the Fulcrum entries', async () => {
    await expect(service.getEntries()).resolves.toEqual([ENTRY]);
  });

  it('fetches the content of a Fulcrum Document', async () => {
    const content = await service.getContent('document-1');

    expect(await content.text()).toBe('%PDF');
    const [documentId, , , options] = api['downloadFulcrumDocument'].mock.calls[0];
    expect(documentId).toBe('document-1');
    expect(skipsGlobalErrorToast(options)).toBe(true);
  });

  it('reads the sync status', async () => {
    await expect(service.getSyncStatus()).resolves.toEqual(RUNNING);
    const [, , options] = api['getFulcrumSyncStatus'].mock.calls[0];
    expect(skipsGlobalErrorToast(options)).toBe(true);
  });

  it('starts a sync without the global toast, a running one answers 409', async () => {
    await expect(service.startSync()).resolves.toEqual(RUNNING);
    const [, , options] = api['startFulcrumSync'].mock.calls[0];
    expect(skipsGlobalErrorToast(options)).toBe(true);
  });
});

function skipsGlobalErrorToast(options: { context: HttpContext }): boolean {
  return options.context.get(SKIP_GLOBAL_ERROR_TOAST);
}
