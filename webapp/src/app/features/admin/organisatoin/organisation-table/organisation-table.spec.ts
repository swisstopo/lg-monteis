import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { PermissionsService } from '@core/auth/permissions.service';
import { ToastService } from '@core/notifications/toast.service';
import OrganisationEdit from '@features/admin/organisatoin/organisation-edit/organisation-edit';
import { OrganisationService } from '@features/admin/organisatoin/services/organisation.service';
import { provideTranslateService } from '@ngx-translate/core';
import { WorkbenchView } from '@scion/workbench';
import { FileDownloadService } from '@ui/file-download/file-download.service';
import { AllCommunityModule, ModuleRegistry } from 'ag-grid-community';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import OrganisationTable from './organisation-table';

// main.ts registers these for the app, the test bed never runs it
ModuleRegistry.registerModules([AllCommunityModule]);

const ORGANISATION_ID = 'organisation-1';

describe('OrganisationTable', () => {
  let dialog: { open: ReturnType<typeof vi.fn> };
  let organisationService: {
    getOrganisations: ReturnType<typeof vi.fn>;
    getOrganisationsCsv: ReturnType<typeof vi.fn>;
    organisationsChanged: ReturnType<typeof signal<boolean>>;
  };
  let fileDownload: { download: ReturnType<typeof vi.fn> };
  let toast: { error: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    dialog = { open: vi.fn() };
    organisationService = {
      getOrganisations: vi.fn().mockResolvedValue({ rows: [], totalCount: 0 }),
      getOrganisationsCsv: vi.fn().mockResolvedValue(new Blob(['name'])),
      organisationsChanged: signal(false),
    };
    fileDownload = { download: vi.fn() };
    toast = { error: vi.fn() };
  });

  async function render() {
    TestBed.configureTestingModule({
      imports: [OrganisationTable],
      providers: [
        provideTranslateService(),
        WorkbenchView,
        { provide: MatDialog, useValue: dialog },
        { provide: OrganisationService, useValue: organisationService },
        { provide: FileDownloadService, useValue: fileDownload },
        { provide: ToastService, useValue: toast },
        { provide: PermissionsService, useValue: { isAdmin: signal(true) } },
      ],
    });
    const fixture = TestBed.createComponent(OrganisationTable);
    await fixture.whenStable();
    return new TableView(fixture, organisationService);
  }

  it('opens the dialog empty to create an organisation', async () => {
    const view = await render();

    view.button('organisation.tableHeader.create')!.click();

    expect(dialog.open).toHaveBeenCalledTimes(1);
    const [component, config] = dialog.open.mock.calls[0];
    expect(component).toBe(OrganisationEdit);
    expect(config.bindings).toBeUndefined();
  });

  it('can only edit once an organisation is selected', async () => {
    const view = await render();
    expect(view.button('organisation.tableHeader.edit')?.disabled).toBe(true);

    await view.select(ORGANISATION_ID);
    expect(view.button('organisation.tableHeader.edit')?.disabled).toBe(false);

    await view.deselect();
    expect(view.button('organisation.tableHeader.edit')?.disabled).toBe(true);
  });

  it('opens the dialog bound to the selected organisation to edit it', async () => {
    const view = await render();
    await view.select(ORGANISATION_ID);

    view.button('organisation.tableHeader.edit')!.click();

    expect(dialog.open).toHaveBeenCalledTimes(1);
    const [component, config] = dialog.open.mock.calls[0];
    expect(component).toBe(OrganisationEdit);
    expect(config.bindings).toHaveLength(1);
  });

  it('reloads its rows and resets the flag whenever an organisation changed', async () => {
    const view = await render();
    await view.gridReady();
    const loadsBefore = organisationService.getOrganisations.mock.calls.length;

    organisationService.organisationsChanged.set(true);
    await view.settle();

    await vi.waitFor(() =>
      expect(organisationService.getOrganisations.mock.calls.length).toBeGreaterThan(loadsBefore),
    );
    expect(organisationService.organisationsChanged()).toBe(false);
  });

  it('filters the name column by the search term', async () => {
    const view = await render();
    await view.gridReady();
    organisationService.getOrganisations.mockClear();

    view.fixture.componentInstance.onSearch('  swiss ');

    await vi.waitFor(() => expect(organisationService.getOrganisations).toHaveBeenCalled());
    const [params] = organisationService.getOrganisations.mock.calls[0];
    expect(params.filterModel).toEqual({
      name: { filterType: 'text', type: 'contains', filter: 'swiss' },
    });
  });

  it('keeps the other column filters when searching', async () => {
    const view = await render();
    await view.gridReady();
    const commentFilter = { filterType: 'text', type: 'contains', filter: 'partner' };
    view.fixture.componentInstance['gridApi']()!.setFilterModel({ comment: commentFilter });

    view.fixture.componentInstance.onSearch('swiss');
    expect(view.fixture.componentInstance['gridApi']()!.getFilterModel()).toEqual({
      comment: commentFilter,
      name: { filterType: 'text', type: 'contains', filter: 'swiss' },
    });

    view.fixture.componentInstance.onSearch('');
    expect(view.fixture.componentInstance['gridApi']()!.getFilterModel()).toEqual({
      comment: commentFilter,
    });
  });

  it('downloads the organisations as CSV', async () => {
    const view = await render();
    await view.gridReady();

    view.button('organisation.tableHeader.download')!.click();

    await vi.waitFor(() => expect(fileDownload.download).toHaveBeenCalled());
    expect(fileDownload.download.mock.calls[0][1]).toBe('organisations.csv');
  });

  it('toasts a failed CSV download', async () => {
    organisationService.getOrganisationsCsv.mockRejectedValue(new Error('500'));
    const view = await render();
    await view.gridReady();

    view.button('organisation.tableHeader.download')!.click();

    await vi.waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith('organisation.error.downloadFailed'),
    );
  });
});

class TableView {
  readonly element: HTMLElement;

  constructor(
    readonly fixture: ComponentFixture<OrganisationTable>,
    private readonly organisationService: { getOrganisations: ReturnType<typeof vi.fn> },
  ) {
    this.element = fixture.nativeElement as HTMLElement;
  }

  labels(): string[] {
    return [...this.element.querySelectorAll('app-table-header button')].map(
      (button) => button.textContent?.trim() ?? '',
    );
  }

  button(label: string): HTMLButtonElement | undefined {
    return [...this.element.querySelectorAll<HTMLButtonElement>('app-table-header button')].find(
      (button) => button.textContent?.includes(label),
    );
  }

  /**
   * Waits until the grid asked for its first rows: it is ready then, and the CSV download needs
   * its filter and sort model.
   */
  async gridReady(): Promise<void> {
    await vi.waitFor(() => expect(this.organisationService.getOrganisations).toHaveBeenCalled());
  }

  async settle(): Promise<void> {
    await this.fixture.whenStable();
  }

  async select(organisationId: string): Promise<void> {
    this.fixture.componentInstance.onSelectionChanged([{ id: organisationId, name: 'x' }]);
    await this.fixture.whenStable();
  }

  async deselect(): Promise<void> {
    this.fixture.componentInstance.onSelectionChanged([]);
    await this.fixture.whenStable();
  }
}
