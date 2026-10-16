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
import { FulcrumEntryDocuments } from './fulcrum-entry-documents';

const REPORT = fulcrumDocument({ id: 'document-report', fileName: 'report.pdf' });
const DATA = fulcrumDocument({
  id: 'document-data',
  fileName: 'data.xlsx',
  contentType: 'application/vnd.ms-excel',
  viewable: false,
});

describe('FulcrumEntryDocuments', () => {
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
      imports: [FulcrumEntryDocuments],
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
    const fixture = TestBed.createComponent(FulcrumEntryDocuments);
    fixture.componentInstance.agInit({
      data: entry,
    } as ICellRendererParams<FulcrumEntryResponseDto>);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    return {
      fixture,
      element,
      find: (testId: string) => element.querySelector<HTMLElement>(`[data-testid="${testId}"]`),
    };
  }

  // mat-menu renders its panel into the overlay container, outside the cell
  function inOverlay(testId: string): HTMLButtonElement[] {
    return [
      ...document.querySelectorAll<HTMLButtonElement>(
        `.cdk-overlay-container [data-testid="${testId}"]`,
      ),
    ];
  }

  describe('an entry with one document', () => {
    it('views the document in a new tab', async () => {
      const view = await render(fulcrumEntry({ documents: [REPORT] }));

      view.find('view-document')!.click();

      await vi.waitFor(() => expect(fileDownload.openInNewTab).toHaveBeenCalled());
      expect(documentService.getContent).toHaveBeenCalledWith('document-report');
    });

    it('downloads the document under its file name', async () => {
      const view = await render(fulcrumEntry({ documents: [REPORT] }));

      view.find('download-document')!.click();

      await vi.waitFor(() => expect(fileDownload.download).toHaveBeenCalled());
      expect(fileDownload.download.mock.calls[0][1]).toBe('report.pdf');
    });

    it('only downloads a document the browser cannot show', async () => {
      const view = await render(fulcrumEntry({ documents: [DATA] }));

      expect(view.find('view-document')).toBeNull();
      expect(view.find('download-document')).not.toBeNull();
    });

    it('toasts a failed download', async () => {
      documentService.getContent.mockRejectedValue(new Error('404'));
      const view = await render(fulcrumEntry({ documents: [REPORT] }));

      view.find('download-document')!.click();

      await vi.waitFor(() =>
        expect(toast.error).toHaveBeenCalledWith('fulcrumEntry.documents.error.download'),
      );
    });
  });

  describe('an entry with several documents', () => {
    it('lists the documents by file name in a menu', async () => {
      const view = await render(fulcrumEntry({ documents: [REPORT, DATA] }));
      expect(view.find('view-document')).toBeNull();

      view.find('documents-menu')!.click();
      await view.fixture.whenStable();

      expect(
        inOverlay('document-item').map((item) => item.querySelector('.file-name')?.textContent),
      ).toEqual(['report.pdf', 'data.xlsx']);
    });

    it('views and downloads a document picked from the menu', async () => {
      const view = await render(fulcrumEntry({ documents: [REPORT, DATA] }));
      view.find('documents-menu')!.click();
      await view.fixture.whenStable();

      inOverlay('document-item')[1].click();
      await view.fixture.whenStable();
      expect(inOverlay('view-document')).toEqual([]);
      inOverlay('download-document')[0].click();

      await vi.waitFor(() => expect(fileDownload.download).toHaveBeenCalled());
      expect(documentService.getContent).toHaveBeenCalledWith('document-data');
      expect(fileDownload.download.mock.calls[0][1]).toBe('data.xlsx');
    });

    it('marks the documents removed from Fulcrum in the menu', async () => {
      const view = await render(
        fulcrumEntry({
          documents: [REPORT, fulcrumDocument({ id: 'document-2', availableInFulcrum: false })],
        }),
      );
      expect(view.find('removed-from-fulcrum')?.getAttribute('aria-label')).toBe(
        'fulcrumEntry.removed.someDocuments',
      );

      view.find('documents-menu')!.click();
      await view.fixture.whenStable();

      expect(inOverlay('document-removed-from-fulcrum')).toHaveLength(1);
    });
  });

  it('offers nothing for an entry whose documents are not downloaded yet', async () => {
    const view = await render(fulcrumEntry({ documents: [] }));

    expect(view.element.querySelector('button')).toBeNull();
    expect(view.find('removed-from-fulcrum')).toBeNull();
  });

  it('shows no document actions without document access, but still the removed marker', async () => {
    const view = await render(
      fulcrumEntry({ availableInFulcrum: false, documents: [REPORT] }),
      false,
    );

    expect(view.find('view-document')).toBeNull();
    expect(view.find('download-document')).toBeNull();
    expect(view.find('removed-from-fulcrum')?.getAttribute('aria-label')).toBe(
      'fulcrumEntry.removed.entry',
    );
  });

  it('marks an entry without documents that was removed from Fulcrum', async () => {
    const view = await render(fulcrumEntry({ availableInFulcrum: false, documents: [] }));

    expect(view.find('removed-from-fulcrum')).not.toBeNull();
  });

  it('marks a single document removed from Fulcrum, still offering its copy', async () => {
    const view = await render(
      fulcrumEntry({ documents: [fulcrumDocument({ availableInFulcrum: false })] }),
    );

    expect(view.find('removed-from-fulcrum')?.getAttribute('aria-label')).toBe(
      'fulcrumEntry.removed.document',
    );
    expect(view.find('view-document')).not.toBeNull();
    expect(view.find('download-document')).not.toBeNull();
  });

  it('shows no marker while the entry and its documents are in Fulcrum', async () => {
    const view = await render(fulcrumEntry({ documents: [REPORT, DATA] }));

    expect(view.find('removed-from-fulcrum')).toBeNull();
  });
});
