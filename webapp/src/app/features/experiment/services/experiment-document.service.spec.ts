import { HttpContext } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ExperimentDocumentControllerService } from '@core/generated';
import { SKIP_GLOBAL_ERROR_TOAST } from '@core/http/http-context';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ExperimentDocumentService } from './experiment-document.service';

const DOCUMENT = { id: 'document-1', fileName: 'report.pdf' };

describe('ExperimentDocumentService', () => {
  let api: Record<string, ReturnType<typeof vi.fn>>;
  let service: ExperimentDocumentService;

  beforeEach(() => {
    api = {
      getDocuments: vi.fn(() => of([DOCUMENT])),
      uploadDocument: vi.fn(() => of(DOCUMENT)),
      downloadDocument: vi.fn(() => of(new Blob(['%PDF']))),
    };
    TestBed.configureTestingModule({
      providers: [{ provide: ExperimentDocumentControllerService, useValue: api }],
    });
    service = TestBed.inject(ExperimentDocumentService);
  });

  it('lists the documents of an experiment', async () => {
    await expect(service.getDocuments('experiment-1')).resolves.toEqual([DOCUMENT]);
    expect(api['getDocuments']).toHaveBeenCalledWith('experiment-1');
  });

  it('uploads a file to an experiment', async () => {
    const file = new File(['%PDF'], 'report.pdf');

    await expect(service.uploadDocument('experiment-1', file)).resolves.toEqual(DOCUMENT);
    const [experimentId, sentFile, , , options] = api['uploadDocument'].mock.calls[0];
    expect([experimentId, sentFile]).toEqual(['experiment-1', file]);
    expect(skipsGlobalErrorToast(options)).toBe(true);
  });

  it('fetches the content of a document', async () => {
    const content = await service.getContent('experiment-1', 'document-1');

    expect(await content.text()).toBe('%PDF');
    const [experimentId, documentId, , , options] = api['downloadDocument'].mock.calls[0];
    expect([experimentId, documentId]).toEqual(['experiment-1', 'document-1']);
    expect(skipsGlobalErrorToast(options)).toBe(true);
  });
});

// the documents section toasts these failures itself, a global toast would be the second one
function skipsGlobalErrorToast(options: { context: HttpContext }): boolean {
  return options.context.get(SKIP_GLOBAL_ERROR_TOAST);
}
