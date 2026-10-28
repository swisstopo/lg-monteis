import { HttpContext } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { SensorDocumentControllerService } from '@core/generated';
import { SKIP_GLOBAL_ERROR_TOAST } from '@core/http/http-context';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SensorDocumentSource } from './sensor-document.source';

const DOCUMENT = { id: 'document-1', fileName: 'report.pdf' };

describe('SensorDocumentSource', () => {
  let api: Record<string, ReturnType<typeof vi.fn>>;
  let service: SensorDocumentSource;

  beforeEach(() => {
    api = {
      getDocuments: vi.fn(() => of([DOCUMENT])),
      uploadDocument: vi.fn(() => of(DOCUMENT)),
      downloadDocument: vi.fn(() => of(new Blob(['%PDF']))),
    };
    TestBed.configureTestingModule({
      providers: [{ provide: SensorDocumentControllerService, useValue: api }],
    });
    service = TestBed.inject(SensorDocumentSource);
  });

  it('lists the documents of a sensor', async () => {
    await expect(service.list('sensor-1')).resolves.toEqual([DOCUMENT]);
    expect(api['getDocuments']).toHaveBeenCalledWith('sensor-1');
  });

  it('uploads a file to a sensor', async () => {
    const file = new File(['%PDF'], 'report.pdf');

    await expect(service.upload('sensor-1', file)).resolves.toEqual(DOCUMENT);
    const [sensorId, sentFile, , , options] = api['uploadDocument'].mock.calls[0];
    expect([sensorId, sentFile]).toEqual(['sensor-1', file]);
    expect(skipsGlobalErrorToast(options)).toBe(true);
  });

  it('fetches the content of a document', async () => {
    const content = await service.content('sensor-1', 'document-1');

    expect(await content.text()).toBe('%PDF');
    const [sensorId, documentId, , , options] = api['downloadDocument'].mock.calls[0];
    expect([sensorId, documentId]).toEqual(['sensor-1', 'document-1']);
    expect(skipsGlobalErrorToast(options)).toBe(true);
  });
});

// the documents section toasts these failures itself, a global toast would be the second one
function skipsGlobalErrorToast(options: { context: HttpContext }): boolean {
  return options.context.get(SKIP_GLOBAL_ERROR_TOAST);
}
