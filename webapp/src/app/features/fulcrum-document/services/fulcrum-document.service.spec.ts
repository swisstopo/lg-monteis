import { HttpContext } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { FulcrumDocumentControllerService } from '@core/generated';
import { SKIP_GLOBAL_ERROR_TOAST } from '@core/http/http-context';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { FulcrumDocumentService } from './fulcrum-document.service';

const DOCUMENT = { id: 'document-1', title: 'Geology of Mont Terri' };
const RUN = { id: 'run-1', status: 'RUNNING', startedAt: '2026-10-15T08:00:00Z' };

describe('FulcrumDocumentService', () => {
  let api: Record<string, ReturnType<typeof vi.fn>>;
  let service: FulcrumDocumentService;

  beforeEach(() => {
    api = {
      getFulcrumDocuments: vi.fn(() => of([DOCUMENT])),
      downloadFulcrumDocumentFile: vi.fn(() => of(new Blob(['%PDF']))),
      getLatestFulcrumSyncRun: vi.fn(() => of(RUN)),
      startFulcrumSync: vi.fn(() => of(RUN)),
    };
    TestBed.configureTestingModule({
      providers: [{ provide: FulcrumDocumentControllerService, useValue: api }],
    });
    service = TestBed.inject(FulcrumDocumentService);
  });

  it('lists the Fulcrum Documents', async () => {
    await expect(service.getDocuments()).resolves.toEqual([DOCUMENT]);
  });

  it('fetches the content of a file', async () => {
    const content = await service.getContent('file-1');

    expect(await content.text()).toBe('%PDF');
    const [fileId, , , options] = api['downloadFulcrumDocumentFile'].mock.calls[0];
    expect(fileId).toBe('file-1');
    expect(skipsGlobalErrorToast(options)).toBe(true);
  });

  it('reads the latest sync run', async () => {
    await expect(service.getLatestSyncRun()).resolves.toEqual(RUN);
    const [, , options] = api['getLatestFulcrumSyncRun'].mock.calls[0];
    expect(skipsGlobalErrorToast(options)).toBe(true);
  });

  it('has no latest sync run when none ever ran', async () => {
    api['getLatestFulcrumSyncRun'].mockReturnValue(of(null));

    await expect(service.getLatestSyncRun()).resolves.toBeUndefined();
  });

  it('starts a sync without the global toast, a running one answers 409', async () => {
    await expect(service.startSync()).resolves.toEqual(RUN);
    const [, , options] = api['startFulcrumSync'].mock.calls[0];
    expect(skipsGlobalErrorToast(options)).toBe(true);
  });
});

function skipsGlobalErrorToast(options: { context: HttpContext }): boolean {
  return options.context.get(SKIP_GLOBAL_ERROR_TOAST);
}
