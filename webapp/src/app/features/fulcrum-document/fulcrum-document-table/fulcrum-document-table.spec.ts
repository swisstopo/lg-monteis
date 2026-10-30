import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { PermissionsService } from '@core/auth/permissions.service';
import { FulcrumDocumentResponseDto } from '@core/generated';
import { ToastService } from '@core/notifications/toast.service';
import { FulcrumDocumentService } from '@features/fulcrum-document/services/fulcrum-document.service';
import {
  fulcrumDocument,
  fulcrumDocumentFile,
  syncStatus,
} from '@features/fulcrum-document/testing/fulcrum-document-fixtures';
import { provideTranslateService } from '@ngx-translate/core';
import { WorkbenchView } from '@scion/workbench';
import { FileDownloadService } from '@ui/file-download/file-download.service';
import Table from '@ui/table/table';
import { AllCommunityModule, ModuleRegistry } from 'ag-grid-community';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import FulcrumDocumentTable from './fulcrum-document-table';

// main.ts registers these for the app, the test bed never runs it
ModuleRegistry.registerModules([AllCommunityModule]);

const CLAY = fulcrumDocument({
  id: 'clay',
  number: 'TR 2019-04',
  title: 'Self-sealing of fractures in Opalinus Clay',
  keywords: ['hydrogeology'],
});
const GAS = fulcrumDocument({
  id: 'gas',
  number: 'TN 2021-11',
  title: 'Gas transport experiment',
  authors: 'Marschall, P.',
  year: 2022,
  keywords: ['two-phase flow'],
  file: fulcrumDocumentFile({
    fileName: 'gas.xlsx',
    contentType: 'application/vnd.ms-excel',
    viewable: false,
  }),
});

describe('FulcrumDocumentTable', () => {
  let documentService: {
    getDocuments: ReturnType<typeof vi.fn>;
    getSyncStatus: ReturnType<typeof vi.fn>;
    startSync: ReturnType<typeof vi.fn>;
    getContent: ReturnType<typeof vi.fn>;
  };
  let fileDownload: { download: ReturnType<typeof vi.fn>; openInNewTab: ReturnType<typeof vi.fn> };
  let toast: { error: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    documentService = {
      getDocuments: vi.fn().mockResolvedValue([CLAY, GAS]),
      getSyncStatus: vi.fn().mockResolvedValue(syncStatus()),
      startSync: vi.fn().mockResolvedValue(syncStatus({ running: true })),
      getContent: vi.fn().mockResolvedValue(new Blob(['%PDF'])),
    };
    fileDownload = {
      download: vi.fn(),
      openInNewTab: vi.fn((load: () => Promise<Blob>) => load()),
    };
    toast = { error: vi.fn() };
  });

  async function render(canAccessDocuments = true) {
    TestBed.configureTestingModule({
      imports: [FulcrumDocumentTable],
      providers: [
        provideTranslateService(),
        WorkbenchView,
        { provide: FulcrumDocumentService, useValue: documentService },
        { provide: FileDownloadService, useValue: fileDownload },
        { provide: ToastService, useValue: toast },
        {
          provide: PermissionsService,
          useValue: { isAdmin: signal(false), canAccessDocuments: signal(canAccessDocuments) },
        },
      ],
    });
    const fixture = TestBed.createComponent(FulcrumDocumentTable);
    await fixture.whenStable();
    const view = new TableView(fixture);
    await view.rowsShown();
    return view;
  }

  it('lists all Fulcrum Documents', async () => {
    const view = await render();

    expect(view.titles()).toEqual([CLAY.title, GAS.title]);
  });

  it('filters by the search term, also on keywords that are no column', async () => {
    const view = await render();

    await view.search('two-phase');
    expect(view.titles()).toEqual([GAS.title]);

    await view.search('TR 2019');
    expect(view.titles()).toEqual([CLAY.title]);

    await view.search('2022');
    expect(view.titles()).toEqual([GAS.title]);

    await view.search('');
    expect(view.titles()).toEqual([CLAY.title, GAS.title]);
  });

  it('shows the list but no refresh, download or view without document access', async () => {
    const view = await render(false);

    expect(view.titles()).toHaveLength(2);
    expect(view.refreshButton()).toBeUndefined();
    expect(view.find('download-file')).toBeNull();
    expect(view.find('view-file')).toBeNull();
  });

  it('offers download and view only once a document is selected', async () => {
    const view = await render();

    expect(view.find('download-file')!.disabled).toBe(true);
    expect(view.find('view-file')!.disabled).toBe(true);

    await view.select(CLAY);

    expect(view.find('download-file')!.disabled).toBe(false);
    expect(view.find('view-file')!.disabled).toBe(false);
  });

  it('views the selected file in a new tab', async () => {
    const view = await render();
    await view.select(CLAY);

    view.find('view-file')!.click();

    await vi.waitFor(() => expect(fileDownload.openInNewTab).toHaveBeenCalled());
    expect(documentService.getContent).toHaveBeenCalledWith('clay');
  });

  it('downloads the selected file under its name', async () => {
    const view = await render();
    await view.select(GAS);

    view.find('download-file')!.click();

    await vi.waitFor(() => expect(fileDownload.download).toHaveBeenCalled());
    expect(documentService.getContent).toHaveBeenCalledWith('gas');
    expect(fileDownload.download.mock.calls[0][1]).toBe('gas.xlsx');
  });

  it('only downloads a file the browser cannot show', async () => {
    const view = await render();
    await view.select(GAS);

    expect(view.find('view-file')!.disabled).toBe(true);
    expect(view.find('download-file')!.disabled).toBe(false);
  });

  it('still offers the file of a document removed from Fulcrum', async () => {
    const view = await render();
    await view.select({ ...CLAY, availableInFulcrum: false });

    expect(view.find('view-file')!.disabled).toBe(false);
    expect(view.find('download-file')!.disabled).toBe(false);
  });

  it('toasts a failed download', async () => {
    documentService.getContent.mockRejectedValue(new Error('404'));
    const view = await render();
    await view.select(CLAY);

    view.find('download-file')!.click();

    await vi.waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith('fulcrumDocument.file.error.download'),
    );
  });

  it('shows an inline error when the documents cannot be loaded', async () => {
    documentService.getDocuments.mockRejectedValue(new Error('500'));
    TestBed.configureTestingModule({
      imports: [FulcrumDocumentTable],
      providers: [
        provideTranslateService(),
        WorkbenchView,
        { provide: FulcrumDocumentService, useValue: documentService },
        {
          provide: PermissionsService,
          useValue: { isAdmin: signal(false), canAccessDocuments: signal(true) },
        },
      ],
    });
    const fixture = TestBed.createComponent(FulcrumDocumentTable);

    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(
        (fixture.nativeElement as HTMLElement).querySelector('app-inline-error'),
      ).not.toBeNull();
    });
  });
});

class TableView {
  private readonly element: HTMLElement;

  constructor(private readonly fixture: ComponentFixture<FulcrumDocumentTable>) {
    this.element = fixture.nativeElement as HTMLElement;
  }

  async rowsShown(): Promise<void> {
    await vi.waitFor(() => expect(this.titles().length).toBeGreaterThan(0));
  }

  // what the grid is handed, its own rendering depends on the layout of the test page
  titles(): (string | undefined)[] {
    const table = this.fixture.debugElement.query(By.directive(Table));
    const rows = (
      table?.componentInstance as Table<FulcrumDocumentResponseDto> | undefined
    )?.rows();
    return (rows ?? []).map((row) => row.title);
  }

  find(testId: string): HTMLButtonElement | null {
    return this.element.querySelector<HTMLButtonElement>(`[data-testid="${testId}"]`);
  }

  // what the grid reports, clicking its checkbox depends on the layout of the test page
  async select(document: FulcrumDocumentResponseDto): Promise<void> {
    const table = this.fixture.debugElement.query(By.directive(Table));
    (table.componentInstance as Table<FulcrumDocumentResponseDto>).selectionChanged.emit([
      document,
    ]);
    this.fixture.detectChanges();
    await this.settle();
  }

  refreshButton(): HTMLButtonElement | undefined {
    return [...this.element.querySelectorAll<HTMLButtonElement>('app-table-header button')].find(
      (button) => button.textContent?.includes('fulcrumSync.refresh'),
    );
  }

  async search(term: string): Promise<void> {
    const input = this.element.querySelector<HTMLInputElement>('app-table-header input')!;
    input.value = term;
    input.dispatchEvent(new Event('input'));
    await this.settle();
    await vi.waitFor(() => expect(this.fixture.componentInstance.searchTerm()).toBe(term));
    await this.settle();
  }

  async settle(): Promise<void> {
    await this.fixture.whenStable();
  }
}
