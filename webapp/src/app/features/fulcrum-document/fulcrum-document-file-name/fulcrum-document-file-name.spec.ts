import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { PermissionsService } from '@core/auth/permissions.service';
import { FulcrumDocumentResponseDto } from '@core/generated';
import { ToastService } from '@core/notifications/toast.service';
import { FulcrumDocumentService } from '@features/fulcrum-document/services/fulcrum-document.service';
import {
  fulcrumDocument,
  fulcrumDocumentFile,
} from '@features/fulcrum-document/testing/fulcrum-document-fixtures';
import { provideTranslateService } from '@ngx-translate/core';
import { FileDownloadService } from '@ui/file-download/file-download.service';
import { ICellRendererParams } from 'ag-grid-community';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { FulcrumDocumentActions } from './fulcrum-document-actions';

const REPORT = fulcrumDocument({
  id: 'document-report',
  file: fulcrumDocumentFile({ fileName: 'report.pdf' }),
});
const DATA = fulcrumDocument({
  id: 'document-data',
  file: fulcrumDocumentFile({
    fileName: 'data.xlsx',
    contentType: 'application/vnd.ms-excel',
    viewable: false,
  }),
});

describe('FulcrumDocumentActions', () => {
  let documentService: { getContent: ReturnType<typeof vi.fn> };
  let fileDownload: { download: ReturnType<typeof vi.fn>; openInNewTab: ReturnType<typeof vi.fn> };
  let toast: { error: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    documentService = { getContent: vi.fn().mockResolvedValue(new Blob(['%PDF'])) };
    fileDownload = {
      download: vi.fn(),
      openInNewTab: vi.fn((load: () => Promise<Blob>) => load()),
    };
    toast = { error: vi.fn() };
  });

  async function render(document: FulcrumDocumentResponseDto, canAccessDocuments = true) {
    TestBed.configureTestingModule({
      imports: [FulcrumDocumentActions],
      providers: [
        provideTranslateService(),
        { provide: FulcrumDocumentService, useValue: documentService },
        { provide: FileDownloadService, useValue: fileDownload },
        { provide: ToastService, useValue: toast },
        {
          provide: PermissionsService,
          useValue: { canAccessDocuments: signal(canAccessDocuments) },
        },
      ],
    });
    const fixture = TestBed.createComponent(FulcrumDocumentActions);
    fixture.componentInstance.agInit({
      data: document,
    } as ICellRendererParams<FulcrumDocumentResponseDto>);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    return (testId: string) => element.querySelector<HTMLElement>(`[data-testid="${testId}"]`);
  }

  it('views the file in a new tab', async () => {
    const find = await render(REPORT);

    find('view-file')!.click();

    await vi.waitFor(() => expect(fileDownload.openInNewTab).toHaveBeenCalled());
    expect(documentService.getContent).toHaveBeenCalledWith('document-report');
  });

  it('downloads the file under its name', async () => {
    const find = await render(REPORT);

    find('download-file')!.click();

    await vi.waitFor(() => expect(fileDownload.download).toHaveBeenCalled());
    expect(documentService.getContent).toHaveBeenCalledWith('document-report');
    expect(fileDownload.download.mock.calls[0][1]).toBe('report.pdf');
  });

  it('only downloads a file the browser cannot show', async () => {
    const find = await render(DATA);

    expect(find('view-file')).toBeNull();
    expect(find('download-file')).not.toBeNull();
  });

  it('toasts a failed download', async () => {
    documentService.getContent.mockRejectedValue(new Error('404'));
    const find = await render(REPORT);

    find('download-file')!.click();

    await vi.waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith('fulcrumDocument.file.error.download'),
    );
  });

  it('offers neither view nor download without document access', async () => {
    const find = await render(REPORT, false);

    expect(find('view-file')).toBeNull();
    expect(find('download-file')).toBeNull();
  });

  it('marks a document removed from Fulcrum, still offering its file', async () => {
    const find = await render({ ...REPORT, availableInFulcrum: false });

    expect(find('removed-from-fulcrum')?.getAttribute('aria-label')).toBe(
      'fulcrumDocument.removed',
    );
    expect(find('view-file')).not.toBeNull();
    expect(find('download-file')).not.toBeNull();
  });

  it('shows the removed marker without document access as well', async () => {
    const find = await render({ ...REPORT, availableInFulcrum: false }, false);

    expect(find('removed-from-fulcrum')).not.toBeNull();
  });

  it('shows no marker while the document is in Fulcrum', async () => {
    const find = await render(REPORT);

    expect(find('removed-from-fulcrum')).toBeNull();
  });
});
