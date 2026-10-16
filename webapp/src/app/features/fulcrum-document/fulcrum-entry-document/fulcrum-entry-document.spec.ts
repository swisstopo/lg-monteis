import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { PermissionsService } from '@core/auth/permissions.service';
import { FulcrumEntryResponseDto } from '@core/generated';
import { ToastService } from '@core/notifications/toast.service';
import { FulcrumDocumentService } from '@features/fulcrum-document/services/fulcrum-document.service';
import {
  fulcrumDocument,
  fulcrumEntry,
} from '@features/fulcrum-document/testing/fulcrum-document-fixtures';
import { provideTranslateService } from '@ngx-translate/core';
import { FileDownloadService } from '@ui/file-download/file-download.service';
import { ICellRendererParams } from 'ag-grid-community';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { FulcrumEntryDocument } from './fulcrum-entry-document';

const REPORT = fulcrumDocument({ id: 'document-report', fileName: 'report.pdf' });
const DATA = fulcrumDocument({
  id: 'document-data',
  fileName: 'data.xlsx',
  contentType: 'application/vnd.ms-excel',
  viewable: false,
});

describe('FulcrumEntryDocument', () => {
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

  async function render(entry: FulcrumEntryResponseDto, canAccessDocuments = true) {
    TestBed.configureTestingModule({
      imports: [FulcrumEntryDocument],
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
    const fixture = TestBed.createComponent(FulcrumEntryDocument);
    fixture.componentInstance.agInit({
      data: entry,
    } as ICellRendererParams<FulcrumEntryResponseDto>);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    return (testId: string) => element.querySelector<HTMLElement>(`[data-testid="${testId}"]`);
  }

  it('views the document in a new tab', async () => {
    const find = await render(fulcrumEntry({ document: REPORT }));

    find('view-document')!.click();

    await vi.waitFor(() => expect(fileDownload.openInNewTab).toHaveBeenCalled());
    expect(documentService.getContent).toHaveBeenCalledWith('document-report');
  });

  it('downloads the document under its file name', async () => {
    const find = await render(fulcrumEntry({ document: REPORT }));

    find('download-document')!.click();

    await vi.waitFor(() => expect(fileDownload.download).toHaveBeenCalled());
    expect(documentService.getContent).toHaveBeenCalledWith('document-report');
    expect(fileDownload.download.mock.calls[0][1]).toBe('report.pdf');
  });

  it('only downloads a document the browser cannot show', async () => {
    const find = await render(fulcrumEntry({ document: DATA }));

    expect(find('view-document')).toBeNull();
    expect(find('download-document')).not.toBeNull();
  });

  it('toasts a failed download', async () => {
    documentService.getContent.mockRejectedValue(new Error('404'));
    const find = await render(fulcrumEntry({ document: REPORT }));

    find('download-document')!.click();

    await vi.waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith('fulcrumEntry.document.error.download'),
    );
  });

  it('offers neither view nor download without document access', async () => {
    const find = await render(fulcrumEntry({ document: REPORT }), false);

    expect(find('view-document')).toBeNull();
    expect(find('download-document')).toBeNull();
  });

  it('marks an entry removed from Fulcrum, still offering its document', async () => {
    const find = await render(fulcrumEntry({ availableInFulcrum: false, document: REPORT }));

    expect(find('removed-from-fulcrum')?.getAttribute('aria-label')).toBe('fulcrumEntry.removed');
    expect(find('view-document')).not.toBeNull();
    expect(find('download-document')).not.toBeNull();
  });

  it('shows the removed marker without document access as well', async () => {
    const find = await render(fulcrumEntry({ availableInFulcrum: false }), false);

    expect(find('removed-from-fulcrum')).not.toBeNull();
  });

  it('shows no marker while the entry is in Fulcrum', async () => {
    const find = await render(fulcrumEntry());

    expect(find('removed-from-fulcrum')).toBeNull();
  });
});
