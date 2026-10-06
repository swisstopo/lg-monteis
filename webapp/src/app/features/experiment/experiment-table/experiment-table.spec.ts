import { computed, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { PermissionsService } from '@core/auth/permissions.service';
import { ToastService } from '@core/notifications/toast.service';
import { openExperimentDialog } from '@features/experiment/experiment-dialog/experiment-dialog';
import { ExperimentService } from '@features/experiment/services/experiment.service';
import { provideTranslateService } from '@ngx-translate/core';
import { WorkbenchView } from '@scion/workbench';
import { FileDownloadService } from '@ui/file-download/file-download.service';
import { AllCommunityModule, ModuleRegistry } from 'ag-grid-community';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ExperimentTable from './experiment-table';

vi.mock('@features/experiment/experiment-dialog/experiment-dialog', () => ({
  openExperimentDialog: vi.fn(),
}));

// main.ts registers these for the app, the test bed never runs it
ModuleRegistry.registerModules([AllCommunityModule]);

const WRITABLE = 'experiment-writable';
const READ_ONLY = 'experiment-read-only';

interface Caller {
  isAdmin: boolean;
  writeExperimentIds: string[];
}

const ADMIN: Caller = { isAdmin: true, writeExperimentIds: [WRITABLE, READ_ONLY] };
const EXPERIMENT_PI: Caller = { isAdmin: false, writeExperimentIds: [WRITABLE] };
const EXPERIMENT_USER: Caller = { isAdmin: false, writeExperimentIds: [] };

describe('ExperimentTable', () => {
  let dialog: { open: ReturnType<typeof vi.fn> };
  let experimentService: {
    getExperiments: ReturnType<typeof vi.fn>;
    getExperimentsCsv: ReturnType<typeof vi.fn>;
    experimentsSaved: ReturnType<typeof signal<number>>;
  };
  let fileDownload: { download: ReturnType<typeof vi.fn> };
  let toast: { error: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    vi.mocked(openExperimentDialog).mockClear();
    dialog = { open: vi.fn() };
    experimentService = {
      getExperiments: vi.fn().mockResolvedValue({ rows: [], totalCount: 0 }),
      getExperimentsCsv: vi.fn().mockResolvedValue(new Blob(['name'])),
      experimentsSaved: signal(0),
    };
    fileDownload = { download: vi.fn() };
    toast = { error: vi.fn() };
  });

  async function render(caller: Caller) {
    const ids = signal(caller.writeExperimentIds);
    TestBed.configureTestingModule({
      imports: [ExperimentTable],
      providers: [
        provideTranslateService(),
        WorkbenchView,
        { provide: MatDialog, useValue: dialog },
        { provide: ExperimentService, useValue: experimentService },
        { provide: FileDownloadService, useValue: fileDownload },
        { provide: ToastService, useValue: toast },
        {
          provide: PermissionsService,
          useValue: {
            isAdmin: signal(caller.isAdmin),
            hasAnyExperimentWriteAccess: computed(() => ids().length > 0),
            canWriteExperiment: (id: string) => ids().includes(id),
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(ExperimentTable);
    await fixture.whenStable();
    return new TableView(fixture);
  }

  it('lets a read-only user view the selected experiment', async () => {
    const view = await render(EXPERIMENT_USER);
    expect(view.button('experiment.tableHeader.view')?.disabled).toBe(true);
    expect(view.button('experiment.tableHeader.edit')).toBeUndefined();

    await view.select(READ_ONLY);
    view.button('experiment.tableHeader.view')!.click();

    expect(openExperimentDialog).toHaveBeenCalledWith(dialog, {
      experimentId: READ_ONLY,
      viewOnly: true,
    });
  });

  it('lets a writer view or edit an experiment they may write', async () => {
    const view = await render(EXPERIMENT_PI);
    await view.select(WRITABLE);

    view.button('experiment.tableHeader.view')!.click();
    view.button('experiment.tableHeader.edit')!.click();

    expect(vi.mocked(openExperimentDialog).mock.calls).toEqual([
      [dialog, { experimentId: WRITABLE, viewOnly: true }],
      [dialog, { experimentId: WRITABLE, viewOnly: false }],
    ]);
  });

  it('lets a writer only view an experiment they may only read', async () => {
    const view = await render(EXPERIMENT_PI);

    await view.select(READ_ONLY);

    expect(view.button('experiment.tableHeader.view')?.disabled).toBe(false);
    expect(view.button('experiment.tableHeader.edit')?.disabled).toBe(true);
  });

  it('lets only an admin create an experiment', async () => {
    expect((await render(EXPERIMENT_PI)).button('experiment.tableHeader.create')).toBeUndefined();
    TestBed.resetTestingModule();

    const view = await render(ADMIN);
    view.button('experiment.tableHeader.create')!.click();

    expect(openExperimentDialog).toHaveBeenCalledWith(dialog);
  });

  it('puts View, the one primary action, last and blue', async () => {
    const view = await render(ADMIN);

    expect(view.labels()).toEqual([
      'experiment.tableHeader.download',
      'experiment.tableHeader.create',
      'experiment.tableHeader.edit',
      'experiment.tableHeader.view',
    ]);
    expect(view.button('experiment.tableHeader.view')?.getAttribute('matbutton')).toBe('filled');
    expect(view.button('experiment.tableHeader.create')?.getAttribute('matbutton')).toBe('tonal');
    expect(view.button('experiment.tableHeader.edit')?.getAttribute('matbutton')).toBe('tonal');
  });

  it('reloads its rows whenever an experiment is saved', async () => {
    const view = await render(EXPERIMENT_USER);
    await view.gridReady(experimentService);
    const loadsBefore = experimentService.getExperiments.mock.calls.length;

    experimentService.experimentsSaved.set(1);
    await view.settle();

    await vi.waitFor(() =>
      expect(experimentService.getExperiments.mock.calls.length).toBeGreaterThan(loadsBefore),
    );
  });

  it('downloads the experiments as CSV', async () => {
    const view = await render(EXPERIMENT_USER);
    await view.gridReady(experimentService);

    view.button('experiment.tableHeader.download')!.click();

    await vi.waitFor(() => expect(fileDownload.download).toHaveBeenCalled());
    expect(fileDownload.download.mock.calls[0][1]).toBe('experiments.csv');
  });

  it('toasts a failed CSV download', async () => {
    experimentService.getExperimentsCsv.mockRejectedValue(new Error('500'));
    const view = await render(EXPERIMENT_USER);
    await view.gridReady(experimentService);

    view.button('experiment.tableHeader.download')!.click();

    await vi.waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith('experiment.error.downloadFailed'),
    );
  });
});

class TableView {
  private readonly element: HTMLElement;

  constructor(private readonly fixture: ComponentFixture<ExperimentTable>) {
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
  async gridReady(experimentService: { getExperiments: ReturnType<typeof vi.fn> }): Promise<void> {
    await vi.waitFor(() => expect(experimentService.getExperiments).toHaveBeenCalled());
  }

  async settle(): Promise<void> {
    await this.fixture.whenStable();
  }

  async select(experimentId: string): Promise<void> {
    this.fixture.componentInstance.onSelectionChanged([{ id: experimentId }]);
    await this.fixture.whenStable();
  }
}
