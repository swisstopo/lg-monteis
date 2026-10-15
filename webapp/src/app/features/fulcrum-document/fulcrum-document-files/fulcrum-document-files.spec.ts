import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { PermissionsService } from '@core/auth/permissions.service';
import { FulcrumDocumentResponseDto } from '@core/generated';
import { ToastService } from '@core/notifications/toast.service';
import { FulcrumDocumentService } from '@features/fulcrum-document/services/fulcrum-document.service';
import {
  file,
  fulcrumDocument,
} from '@features/fulcrum-document/testing/fulcrum-document-fixtures';
import { provideTranslateService } from '@ngx-translate/core';
import { FileDownloadService } from '@ui/file-download/file-download.service';
import { ICellRendererParams } from 'ag-grid-community';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { FulcrumDocumentFiles } from './fulcrum-document-files';

const REPORT = file({ id: 'file-report', fileName: 'report.pdf' });
const DATA = file({
  id: 'file-data',
  fileName: 'data.xlsx',
  contentType: 'application/vnd.ms-excel',
  viewable: false,
});

describe('FulcrumDocumentFiles', () => {
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
      imports: [FulcrumDocumentFiles],
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
    const fixture = TestBed.createComponent(FulcrumDocumentFiles);
    fixture.componentInstance.agInit({
      data: document,
    } as ICellRendererParams<FulcrumDocumentResponseDto>);
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

  describe('a document with one file', () => {
    it('views the file in a new tab', async () => {
      const view = await render(fulcrumDocument({ files: [REPORT] }));

      view.find('view-file')!.click();

      await vi.waitFor(() => expect(fileDownload.openInNewTab).toHaveBeenCalled());
      expect(documentService.getContent).toHaveBeenCalledWith('file-report');
    });

    it('downloads the file under its name', async () => {
      const view = await render(fulcrumDocument({ files: [REPORT] }));

      view.find('download-file')!.click();

      await vi.waitFor(() => expect(fileDownload.download).toHaveBeenCalled());
      expect(fileDownload.download.mock.calls[0][1]).toBe('report.pdf');
    });

    it('only downloads a file the browser cannot show', async () => {
      const view = await render(fulcrumDocument({ files: [DATA] }));

      expect(view.find('view-file')).toBeNull();
      expect(view.find('download-file')).not.toBeNull();
    });

    it('offers nothing for a file whose content is not stored in MONTEIS', async () => {
      const view = await render(fulcrumDocument({ files: [file({ stored: false })] }));

      expect(view.find('view-file')).toBeNull();
      expect(view.find('download-file')).toBeNull();
    });

    it('toasts a failed download', async () => {
      documentService.getContent.mockRejectedValue(new Error('404'));
      const view = await render(fulcrumDocument({ files: [REPORT] }));

      view.find('download-file')!.click();

      await vi.waitFor(() =>
        expect(toast.error).toHaveBeenCalledWith('fulcrumDocument.files.error.download'),
      );
    });
  });

  describe('a document with several files', () => {
    it('lists the files by name in a menu', async () => {
      const view = await render(fulcrumDocument({ files: [REPORT, DATA] }));
      expect(view.find('view-file')).toBeNull();

      view.find('files-menu')!.click();
      await view.fixture.whenStable();

      expect(
        inOverlay('file-entry').map((entry) => entry.querySelector('.file-name')?.textContent),
      ).toEqual(['report.pdf', 'data.xlsx']);
    });

    it('views and downloads a file picked from the menu', async () => {
      const view = await render(fulcrumDocument({ files: [REPORT, DATA] }));
      view.find('files-menu')!.click();
      await view.fixture.whenStable();

      inOverlay('file-entry')[1].click();
      await view.fixture.whenStable();
      expect(inOverlay('view-file')).toEqual([]);
      inOverlay('download-file')[0].click();

      await vi.waitFor(() => expect(fileDownload.download).toHaveBeenCalled());
      expect(documentService.getContent).toHaveBeenCalledWith('file-data');
      expect(fileDownload.download.mock.calls[0][1]).toBe('data.xlsx');
    });

    it('disables a file whose content is not stored in MONTEIS', async () => {
      const view = await render(
        fulcrumDocument({ files: [REPORT, file({ id: 'file-2', stored: false })] }),
      );
      view.find('files-menu')!.click();
      await view.fixture.whenStable();

      expect(inOverlay('file-entry').map((entry) => entry.disabled)).toEqual([false, true]);
    });

    it('marks the files removed from Fulcrum in the menu', async () => {
      const view = await render(
        fulcrumDocument({
          files: [REPORT, file({ id: 'file-2', availableInFulcrum: false })],
        }),
      );
      expect(view.find('removed-from-fulcrum')?.getAttribute('aria-label')).toBe(
        'fulcrumDocument.removed.someFiles',
      );

      view.find('files-menu')!.click();
      await view.fixture.whenStable();

      expect(inOverlay('file-removed-from-fulcrum')).toHaveLength(1);
    });
  });

  it('shows no file actions without document access, but still the removed marker', async () => {
    const view = await render(
      fulcrumDocument({ availableInFulcrum: false, files: [REPORT] }),
      false,
    );

    expect(view.find('view-file')).toBeNull();
    expect(view.find('download-file')).toBeNull();
    expect(view.find('removed-from-fulcrum')?.getAttribute('aria-label')).toBe(
      'fulcrumDocument.removed.document',
    );
  });

  it('marks a single file removed from Fulcrum, still offering its stored copy', async () => {
    const view = await render(fulcrumDocument({ files: [file({ availableInFulcrum: false })] }));

    expect(view.find('removed-from-fulcrum')?.getAttribute('aria-label')).toBe(
      'fulcrumDocument.removed.file',
    );
    expect(view.find('view-file')).not.toBeNull();
    expect(view.find('download-file')).not.toBeNull();
  });

  it('shows no marker while the document and its files are in Fulcrum', async () => {
    const view = await render(fulcrumDocument({ files: [REPORT, DATA] }));

    expect(view.find('removed-from-fulcrum')).toBeNull();
  });
});
