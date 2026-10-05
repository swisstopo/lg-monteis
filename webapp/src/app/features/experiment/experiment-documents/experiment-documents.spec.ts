import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ErrorDto, ExperimentDocumentResponseDto } from '@core/generated';
import { ToastService } from '@core/notifications/toast.service';
import { ExperimentDocumentService } from '@features/experiment/services/experiment-document.service';
import { provideTranslateService } from '@ngx-translate/core';
import { FileDownloadService } from '@ui/file-download/file-download.service';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ExperimentDocuments } from './experiment-documents';

const REPORT: ExperimentDocumentResponseDto = {
  id: 'document-1',
  fileName: 'report.pdf',
  uploadedAt: '2026-02-13T09:03:26Z',
  uploadedBy: 'alice',
};

describe('ExperimentDocuments', () => {
  let documentService: {
    getDocuments: ReturnType<typeof vi.fn>;
    uploadDocument: ReturnType<typeof vi.fn>;
    getContent: ReturnType<typeof vi.fn>;
  };
  let fileDownload: { download: ReturnType<typeof vi.fn>; openInNewTab: ReturnType<typeof vi.fn> };
  let toast: { error: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    documentService = {
      getDocuments: vi.fn().mockResolvedValue([REPORT]),
      uploadDocument: vi.fn().mockResolvedValue(REPORT),
      getContent: vi.fn().mockResolvedValue(new Blob(['%PDF'])),
    };
    fileDownload = {
      download: vi.fn(),
      openInNewTab: vi.fn((load: () => Promise<Blob>) => load()),
    };
    toast = { error: vi.fn() };
    TestBed.configureTestingModule({
      imports: [ExperimentDocuments],
      providers: [
        provideTranslateService(),
        { provide: ExperimentDocumentService, useValue: documentService },
        { provide: FileDownloadService, useValue: fileDownload },
        { provide: ToastService, useValue: toast },
      ],
    });
  });

  async function render(readOnly = false) {
    const fixture = TestBed.createComponent(ExperimentDocuments);
    fixture.componentRef.setInput('experimentId', 'experiment-1');
    fixture.componentRef.setInput('readOnly', readOnly);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    return {
      fixture,
      element,
      addButton: () =>
        [...element.querySelectorAll('button')].find((button) =>
          button.textContent?.includes('experiment.documents.add'),
        ),
      fileInput: () => element.querySelector<HTMLInputElement>('input[type="file"]'),
    };
  }

  async function selectFiles(view: Awaited<ReturnType<typeof render>>, ...files: File[]) {
    const input = view.fileInput()!;
    const transfer = new DataTransfer();
    files.forEach((file) => transfer.items.add(file));
    input.files = transfer.files;
    input.dispatchEvent(new Event('change'));
    await vi.waitFor(() => expect(documentService.getDocuments).toHaveBeenCalledTimes(2));
    await view.fixture.whenStable();
  }

  it('lists the documents with upload time and uploader', async () => {
    const { element } = await render();

    expect(documentService.getDocuments).toHaveBeenCalledWith('experiment-1');
    expect(element.querySelector('a')?.textContent).toContain('report.pdf');
    expect(element.querySelector('.document-meta')?.textContent).toContain('alice');
  });

  it('says so when there are no documents', async () => {
    documentService.getDocuments.mockResolvedValue([]);

    const { element } = await render();

    expect(element.textContent).toContain('experiment.documents.empty');
  });

  it('says so when the documents cannot be loaded', async () => {
    documentService.getDocuments.mockRejectedValue(new Error('500'));

    const { element } = await render();

    expect(element.textContent).toContain('experiment.documents.error.load');
  });

  it('offers no upload in read-only mode', async () => {
    const view = await render(true);

    expect(view.addButton()).toBeUndefined();
    expect(view.fileInput()).toBeNull();
  });

  it('opens the file chooser from Add Document', async () => {
    const view = await render();
    const click = vi.spyOn(view.fileInput()!, 'click').mockImplementation(() => {});

    view.addButton()!.click();

    expect(click).toHaveBeenCalled();
  });

  it('uploads the selected files one after the other and reloads the list', async () => {
    const view = await render();
    const first = new File(['a'], 'a.pdf');
    const second = new File(['b'], 'b.pdf');

    await selectFiles(view, first, second);

    expect(documentService.uploadDocument.mock.calls).toEqual([
      ['experiment-1', first],
      ['experiment-1', second],
    ]);
    expect(view.fileInput()!.value).toBe('');
  });

  it('toasts every failed upload with its file name, the interceptor is skipped', async () => {
    documentService.uploadDocument
      .mockRejectedValueOnce(
        new HttpErrorResponse({
          status: 422,
          error: [{ messageKey: 'document.validation.empty', target: ErrorDto.TargetEnum.Form }],
        }),
      )
      .mockRejectedValueOnce(
        new HttpErrorResponse({
          status: 403,
          error: [{ messageKey: 'access.denied', target: ErrorDto.TargetEnum.Global }],
        }),
      );
    const view = await render();

    await selectFiles(view, new File([], 'empty.pdf'), new File(['x'], 'forbidden.pdf'));

    expect(toast.error.mock.calls).toEqual([
      ['document.validation.empty', 'empty.pdf'],
      ['access.denied', 'forbidden.pdf'],
    ]);
  });

  it('toasts the generic upload error when the response carries no message', async () => {
    documentService.uploadDocument.mockRejectedValue(
      new HttpErrorResponse({ status: 0, error: new ProgressEvent('error') }),
    );
    const view = await render();

    await selectFiles(view, new File(['x'], 'report.pdf'));

    expect(toast.error).toHaveBeenCalledWith('experiment.documents.error.upload', 'report.pdf');
  });

  it('toasts the generic upload error for an error without body', async () => {
    documentService.uploadDocument.mockRejectedValue(new HttpErrorResponse({ status: 500 }));
    const view = await render();

    await selectFiles(view, new File(['x'], 'report.pdf'));

    expect(toast.error).toHaveBeenCalledWith('experiment.documents.error.upload', 'report.pdf');
  });

  it('downloads a document from its name', async () => {
    const { element } = await render();

    element.querySelector('a')!.click();

    await vi.waitFor(() => expect(fileDownload.download).toHaveBeenCalled());
    expect(documentService.getContent).toHaveBeenCalledWith('experiment-1', 'document-1');
    expect(fileDownload.download.mock.calls[0][1]).toBe('report.pdf');
  });

  it('opens a document in a new tab from the eye', async () => {
    const { element } = await render();

    element.querySelector<HTMLButtonElement>('.document-name button')!.click();

    await vi.waitFor(() => expect(fileDownload.openInNewTab).toHaveBeenCalled());
    expect(documentService.getContent).toHaveBeenCalledWith('experiment-1', 'document-1');
  });

  it('toasts a failed download', async () => {
    documentService.getContent.mockRejectedValue(new Error('404'));
    const { element } = await render();

    element.querySelector('a')!.click();

    await vi.waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith('experiment.documents.error.download'),
    );
    expect(fileDownload.download).not.toHaveBeenCalled();
  });

  it('toasts a failed view', async () => {
    fileDownload.openInNewTab.mockRejectedValue(new Error('404'));
    const { element } = await render();

    element.querySelector<HTMLButtonElement>('.document-name button')!.click();

    await vi.waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith('experiment.documents.error.download'),
    );
  });
});
