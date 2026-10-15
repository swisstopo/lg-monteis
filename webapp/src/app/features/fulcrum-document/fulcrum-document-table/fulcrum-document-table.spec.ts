import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { PermissionsService } from '@core/auth/permissions.service';
import { FulcrumDocumentResponseDto } from '@core/generated';
import { SYNC_POLL_INTERVAL_MS } from '@features/fulcrum-document/fulcrum-sync/fulcrum-sync';
import { FulcrumDocumentService } from '@features/fulcrum-document/services/fulcrum-document.service';
import {
  fulcrumDocument,
  syncRun,
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
  keywords: ['two-phase flow'],
});

describe('FulcrumDocumentTable', () => {
  let documentService: {
    getDocuments: ReturnType<typeof vi.fn>;
    getLatestSyncRun: ReturnType<typeof vi.fn>;
    startSync: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    documentService = {
      getDocuments: vi.fn().mockResolvedValue([CLAY, GAS]),
      getLatestSyncRun: vi.fn().mockResolvedValue(syncRun()),
      startSync: vi.fn().mockResolvedValue(syncRun({ status: 'RUNNING' })),
    };
  });

  async function render(canAccessDocuments = true) {
    TestBed.configureTestingModule({
      imports: [FulcrumDocumentTable],
      providers: [
        provideTranslateService(),
        WorkbenchView,
        { provide: SYNC_POLL_INTERVAL_MS, useValue: 1 },
        { provide: FulcrumDocumentService, useValue: documentService },
        { provide: FileDownloadService, useValue: { download: vi.fn(), openInNewTab: vi.fn() } },
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

    await view.search('');
    expect(view.titles()).toEqual([CLAY.title, GAS.title]);
  });

  it('shows the list but no refresh without document access', async () => {
    const view = await render(false);

    expect(view.titles()).toHaveLength(2);
    expect(view.refreshButton()).toBeUndefined();
  });

  it('reloads the documents once a sync finished', async () => {
    documentService.getLatestSyncRun
      .mockResolvedValueOnce(syncRun())
      .mockResolvedValueOnce(syncRun({ status: 'SUCCEEDED' }));
    const view = await render();
    documentService.getDocuments.mockResolvedValue([GAS]);

    view.refreshButton()!.click();

    await vi.waitFor(() => expect(documentService.getDocuments).toHaveBeenCalledTimes(2));
    await vi.waitFor(async () => {
      await view.settle();
      expect(view.titles()).toEqual([GAS.title]);
    });
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

  refreshButton(): HTMLButtonElement | undefined {
    return [...this.element.querySelectorAll<HTMLButtonElement>('app-table-header button')].find(
      (button) => button.textContent?.includes('fulcrumDocument.sync.refresh'),
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
