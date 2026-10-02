import { TestBed } from '@angular/core/testing';
import { ExperimentDocumentControllerService } from '@core/generated';
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
    expect(api['uploadDocument']).toHaveBeenCalledWith('experiment-1', file);
  });

  it('fetches the content of a document', async () => {
    const content = await service.getContent('experiment-1', 'document-1');

    expect(await content.text()).toBe('%PDF');
    expect(api['downloadDocument']).toHaveBeenCalledWith('experiment-1', 'document-1');
  });
});
