import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { DocumentResponseDto, ErrorDto } from '@core/generated';
import { ToastService } from '@core/notifications/toast.service';
import { provideTranslateService, TranslateService } from '@ngx-translate/core';
import { FileDownloadService } from '@ui/file-download/file-download.service';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DocumentSource } from './document-source';
import { Documents } from './documents';
import { MAX_DOCUMENT_SIZE_BYTES, MAX_DOCUMENT_SIZE_MB } from './max-document-size';

const REPORT: DocumentResponseDto = {
  id: 'document-1',
  fileName: 'report.pdf',
  viewable: true,
  uploadedAt: '2026-02-13T09:03:26Z',
  uploadedBy: 'alice',
};

describe('Documents', () => {
  let source: {
    list: ReturnType<typeof vi.fn>;
    upload: ReturnType<typeof vi.fn>;
    content: ReturnType<typeof vi.fn>;
  };
  let fileDownload: { download: ReturnType<typeof vi.fn>; openInNewTab: ReturnType<typeof vi.fn> };
  let toast: { error: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    source = {
      list: vi.fn().mockResolvedValue([REPORT]),
      upload: vi.fn().mockResolvedValue(REPORT),
      content: vi.fn().mockResolvedValue(new Blob(['%PDF'])),
    };
    fileDownload = {
      download: vi.fn(),
      openInNewTab: vi.fn((load: () => Promise<Blob>) => load()),
    };
    toast = { error: vi.fn() };
    TestBed.configureTestingModule({
      imports: [Documents],
      providers: [
        provideTranslateService(),
        { provide: DocumentSource, useValue: source },
        { provide: FileDownloadService, useValue: fileDownload },
        { provide: ToastService, useValue: toast },
      ],
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  async function render(readOnly = false) {
    const fixture = TestBed.createComponent(Documents);
    fixture.componentRef.setInput('ownerId', 'owner-1');
    fixture.componentRef.setInput('readOnly', readOnly);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    return {
      fixture,
      element,
      addButton: () =>
        [...element.querySelectorAll('button')].find((button) =>
          button.textContent?.includes('document.add'),
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
    await vi.waitFor(() => expect(source.list).toHaveBeenCalledTimes(2));
    await view.fixture.whenStable();
  }

  it('lists the documents with upload time and uploader', async () => {
    const { element } = await render();

    expect(source.list).toHaveBeenCalledWith('owner-1');
    expect(element.querySelector('.file-name')?.textContent).toContain('report.pdf');
    expect(element.querySelector('.document-meta')?.textContent).toContain('alice');
  });

  it('says so when there are no documents', async () => {
    source.list.mockResolvedValue([]);

    const { element } = await render();

    expect(element.textContent).toContain('document.empty');
  });

  it('says so when the documents cannot be loaded', async () => {
    source.list.mockRejectedValue(new Error('500'));

    const { element } = await render();

    expect(element.textContent).toContain('document.error.load');
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

    expect(source.upload.mock.calls).toEqual([
      ['owner-1', first],
      ['owner-1', second],
    ]);
    expect(view.fileInput()!.value).toBe('');
  });

  it('toasts every failed upload with its file name, the interceptor is skipped', async () => {
    source.upload
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

  it('toasts the size limit of a too large upload', async () => {
    source.upload.mockRejectedValue(
      new HttpErrorResponse({
        status: 413,
        error: {
          messageKey: 'document.validation.tooLarge',
          params: { max: '50 MB' },
          target: ErrorDto.TargetEnum.Form,
        },
      }),
    );
    const translate = vi.spyOn(TestBed.inject(TranslateService), 'translate');
    const view = await render();

    await selectFiles(view, new File(['x'], 'huge.pdf'));

    expect(toast.error).toHaveBeenCalledWith('document.validation.tooLarge', 'huge.pdf');
    expect(translate).toHaveBeenCalledWith('document.validation.tooLarge', { max: '50 MB' });
  });

  it('toasts the generic upload error when the response carries no message', async () => {
    source.upload.mockRejectedValue(
      new HttpErrorResponse({ status: 0, error: new ProgressEvent('error') }),
    );
    const view = await render();

    await selectFiles(view, new File(['x'], 'report.pdf'));

    expect(toast.error).toHaveBeenCalledWith('document.error.upload', 'report.pdf');
  });

  it('refuses a file over the size limit without uploading it', async () => {
    vi.spyOn(Blob.prototype, 'size', 'get').mockReturnValue(MAX_DOCUMENT_SIZE_BYTES + 1);
    const translate = vi.spyOn(TestBed.inject(TranslateService), 'translate');
    const view = await render();

    await selectFiles(view, new File(['x'], 'huge.pdf'));

    expect(source.upload).not.toHaveBeenCalled();
    expect(toast.error).toHaveBeenCalledWith('document.validation.tooLarge', 'huge.pdf');
    expect(translate).toHaveBeenCalledWith('document.validation.tooLarge', {
      max: `${MAX_DOCUMENT_SIZE_MB} MB`,
    });
  });

  it('toasts the generic upload error for an error without body', async () => {
    source.upload.mockRejectedValue(new HttpErrorResponse({ status: 500 }));
    const view = await render();

    await selectFiles(view, new File(['x'], 'report.pdf'));

    expect(toast.error).toHaveBeenCalledWith('document.error.upload', 'report.pdf');
  });

  it('downloads a document from its name', async () => {
    const { element } = await render();

    element.querySelector<HTMLButtonElement>('.file-name')!.click();

    await vi.waitFor(() => expect(fileDownload.download).toHaveBeenCalled());
    expect(source.content).toHaveBeenCalledWith('owner-1', 'document-1');
    expect(fileDownload.download.mock.calls[0][1]).toBe('report.pdf');
  });

  it('opens a document in a new tab from the eye', async () => {
    const { element } = await render();

    element.querySelector<HTMLButtonElement>('.view')!.click();

    await vi.waitFor(() => expect(fileDownload.openInNewTab).toHaveBeenCalled());
    expect(source.content).toHaveBeenCalledWith('owner-1', 'document-1');
  });

  it('offers only the download for a document the browser must not open', async () => {
    source.list.mockResolvedValue([{ ...REPORT, fileName: 'page.html', viewable: false }]);

    const { element } = await render();

    expect(element.querySelector('.view')).toBeNull();
    expect(element.querySelector('.file-name')?.textContent).toContain('page.html');
  });

  it('toasts a failed download', async () => {
    source.content.mockRejectedValue(new Error('404'));
    const { element } = await render();

    element.querySelector<HTMLButtonElement>('.file-name')!.click();

    await vi.waitFor(() => expect(toast.error).toHaveBeenCalledWith('document.error.download'));
    expect(fileDownload.download).not.toHaveBeenCalled();
  });

  it('toasts a failed view', async () => {
    fileDownload.openInNewTab.mockRejectedValue(new Error('404'));
    const { element } = await render();

    element.querySelector<HTMLButtonElement>('.view')!.click();

    await vi.waitFor(() => expect(toast.error).toHaveBeenCalledWith('document.error.download'));
  });
});
