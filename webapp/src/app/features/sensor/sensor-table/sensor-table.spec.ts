import { computed, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PermissionsService } from '@core/auth/permissions.service';
import {
  ExperimentControllerService,
  FulcrumRefreshResponseDto,
  SensorControllerService,
  SensorParameterRowResponseDto,
} from '@core/generated';
import { ToastService } from '@core/notifications/toast.service';
import { provideTranslateService, TranslateService } from '@ngx-translate/core';
import { WorkbenchView } from '@scion/workbench';
import { FormDialogService } from '@ui/dialog/form-dialog.service';
import { FileDownloadService } from '@ui/file-download/file-download.service';
import { AllCommunityModule, ModuleRegistry } from 'ag-grid-community';
import { Observable, of, Subject, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import SensorTable from './sensor-table';

// main.ts registers these for the app, the test bed never runs it
ModuleRegistry.registerModules([AllCommunityModule]);

const REFRESH_SELECTED = 'Refresh selected from Fulcrum';
const REFRESH_ALL = 'Refresh all from Fulcrum';

const TRANSLATIONS = {
  sensor: {
    tableHeader: {
      edit: 'Edit Sensor',
      view: 'View',
      refreshSelected: REFRESH_SELECTED,
      refreshAll: REFRESH_ALL,
    },
    fulcrumRefresh: {
      success: '{{checked}} checked, {{updated}} updated.',
      partial: '{{checked}} checked, {{updated}} updated. Skipped: {{skippedSensors}}.',
      andMore: 'and {{count}} more',
      reason: {
        NOT_FOUND: 'not found',
        NOT_IN_FULCRUM: 'not in Fulcrum',
        COORDINATES_MISSING: 'coordinates missing',
        CHANGED_MEANWHILE: 'changed meanwhile',
      },
    },
  },
};

interface Caller {
  isAdmin: boolean;
  writeExperimentIds: string[];
}

const EXPERIMENT_PI: Caller = { isAdmin: false, writeExperimentIds: ['experiment-1'] };
const EXPERIMENT_USER: Caller = { isAdmin: false, writeExperimentIds: [] };

function row(sensorId: string, parameterId?: string): SensorParameterRowResponseDto {
  return {
    sensorId,
    name: `Sensor ${sensorId}`,
    parameter: parameterId ? { id: parameterId } : undefined,
  };
}

describe('SensorTable', () => {
  let api: Record<string, ReturnType<typeof vi.fn>>;
  let toast: Record<'success' | 'warning' | 'error', ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    api = {
      getSensors: vi.fn(() => of({ rows: [], totalCount: 0 })),
      findAllFormulas: vi.fn(() => of([])),
      findAllTypes: vi.fn(() => of([])),
      refreshFromFulcrum: vi.fn(() => of({ checked: 0, updated: 0, skipped: [] })),
    };
    toast = { success: vi.fn(), warning: vi.fn(), error: vi.fn() };
  });

  async function render(caller: Caller = EXPERIMENT_PI) {
    const ids = signal(caller.writeExperimentIds);
    TestBed.configureTestingModule({
      imports: [SensorTable],
      providers: [
        provideTranslateService(),
        WorkbenchView,
        { provide: SensorControllerService, useValue: api },
        { provide: ExperimentControllerService, useValue: {} },
        { provide: FormDialogService, useValue: { open: vi.fn() } },
        { provide: FileDownloadService, useValue: { download: vi.fn() } },
        { provide: ToastService, useValue: toast },
        {
          provide: PermissionsService,
          useValue: {
            isAdmin: signal(caller.isAdmin),
            hasAnyExperimentWriteAccess: computed(() => caller.isAdmin || ids().length > 0),
          },
        },
      ],
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', TRANSLATIONS);
    translate.use('en');
    const fixture = TestBed.createComponent(SensorTable);
    await fixture.whenStable();
    return new TableView(fixture, api);
  }

  function refreshAnswers(result: FulcrumRefreshResponseDto | Observable<never>) {
    api['refreshFromFulcrum'].mockReturnValue(result instanceof Observable ? result : of(result));
  }

  it('offers no refresh to a user who can write no experiment', async () => {
    const view = await render(EXPERIMENT_USER);

    expect(view.button(REFRESH_ALL)).toBeUndefined();
    expect(view.button(REFRESH_SELECTED)).toBeUndefined();
  });

  it('refreshes every visible sensor when nothing is selected', async () => {
    const view = await render();

    view.button(REFRESH_ALL)!.click();

    await vi.waitFor(() => expect(api['refreshFromFulcrum']).toHaveBeenCalledWith({}));
  });

  it('refreshes the selected sensors once each, however many of their rows are selected', async () => {
    const view = await render();

    await view.select(row('sensor-1', 'p1'), row('sensor-1', 'p2'), row('sensor-2'));
    view.button(REFRESH_SELECTED)!.click();

    await vi.waitFor(() =>
      expect(api['refreshFromFulcrum']).toHaveBeenCalledWith({
        sensorIds: ['sensor-1', 'sensor-2'],
      }),
    );
    expect(view.button(REFRESH_ALL)).toBeUndefined();
  });

  it('toasts the counts when no sensor was skipped', async () => {
    refreshAnswers({ checked: 3, updated: 1, skipped: [] });
    const view = await render();

    view.button(REFRESH_ALL)!.click();

    await vi.waitFor(() => expect(toast.success).toHaveBeenCalledWith('3 checked, 1 updated.'));
    expect(toast.warning).not.toHaveBeenCalled();
  });

  it('warns with the skipped sensors and why they were skipped', async () => {
    refreshAnswers({
      checked: 2,
      updated: 0,
      skipped: [
        { sensorId: 'sensor-1', sensorName: 'Inclinometer 1', reason: 'NOT_IN_FULCRUM' },
        { sensorId: 'sensor-2', sensorName: 'Inclinometer 2', reason: 'CHANGED_MEANWHILE' },
      ],
    });
    const view = await render();

    await view.select(row('sensor-1'), row('sensor-2'));
    view.button(REFRESH_SELECTED)!.click();

    await vi.waitFor(() =>
      expect(toast.warning).toHaveBeenCalledWith(
        '2 checked, 0 updated. Skipped: Inclinometer 1 (not in Fulcrum), ' +
          'Inclinometer 2 (changed meanwhile).',
      ),
    );
    expect(toast.success).not.toHaveBeenCalled();
  });

  it('names at most 10 skipped sensors and counts the rest', async () => {
    const selected = Array.from({ length: 12 }, (_, i) => row(`s${i + 1}`));
    refreshAnswers({
      checked: 12,
      updated: 0,
      skipped: selected.map((sensor) => ({
        sensorId: sensor.sensorId,
        sensorName: sensor.name,
        reason: 'COORDINATES_MISSING' as const,
      })),
    });
    const view = await render();

    await view.select(...selected);
    view.button(REFRESH_SELECTED)!.click();

    await vi.waitFor(() => expect(toast.warning).toHaveBeenCalled());
    const message: string = toast.warning.mock.calls[0][0];
    expect(message).toContain(
      'Sensor s9 (coordinates missing), Sensor s10 (coordinates missing), and 2 more.',
    );
    expect(message).not.toContain('Sensor s11');
  });

  it('names skipped sensors outside the loaded rows after a refresh of all', async () => {
    refreshAnswers({
      checked: 1,
      updated: 0,
      skipped: [{ sensorId: 'unloaded', sensorName: 'Piezometer 7', reason: 'NOT_IN_FULCRUM' }],
    });
    const view = await render();

    view.button(REFRESH_ALL)!.click();

    await vi.waitFor(() =>
      expect(toast.warning).toHaveBeenCalledWith(
        '1 checked, 0 updated. Skipped: Piezometer 7 (not in Fulcrum).',
      ),
    );
  });

  it('names a sensor that was not found by its id', async () => {
    refreshAnswers({
      checked: 1,
      updated: 0,
      skipped: [{ sensorId: 'unknown', sensorName: null, reason: 'NOT_FOUND' }],
    });
    const view = await render();

    view.button(REFRESH_ALL)!.click();

    await vi.waitFor(() =>
      expect(toast.warning).toHaveBeenCalledWith(
        '1 checked, 0 updated. Skipped: unknown (not found).',
      ),
    );
  });

  it('disables the refresh while it runs', async () => {
    const answer = new Subject<FulcrumRefreshResponseDto>();
    api['refreshFromFulcrum'].mockReturnValue(answer);
    const view = await render();

    view.button(REFRESH_ALL)!.click();
    await view.settle();
    expect(view.button(REFRESH_ALL)?.disabled).toBe(true);

    answer.next({ checked: 0, updated: 0, skipped: [] });
    answer.complete();
    await vi.waitFor(() => expect(view.button(REFRESH_ALL)?.disabled).toBe(false));
  });

  it('leaves a failed refresh to the global error toast, re-enables it and reloads the rows', async () => {
    refreshAnswers(throwError(() => new Error('502')));
    const view = await render();
    await view.gridReady();
    const loadsBefore = api['getSensors'].mock.calls.length;

    view.button(REFRESH_ALL)!.click();

    await vi.waitFor(() =>
      expect(api['getSensors'].mock.calls.length).toBeGreaterThan(loadsBefore),
    );
    await view.settle();
    expect(view.button(REFRESH_ALL)?.disabled).toBe(false);
    expect(toast.success).not.toHaveBeenCalled();
    expect(toast.warning).not.toHaveBeenCalled();
    expect(toast.error).not.toHaveBeenCalled();
  });

  it('reloads the rows after a refresh', async () => {
    const view = await render();
    await view.gridReady();
    const loadsBefore = api['getSensors'].mock.calls.length;

    view.button(REFRESH_ALL)!.click();

    await vi.waitFor(() =>
      expect(api['getSensors'].mock.calls.length).toBeGreaterThan(loadsBefore),
    );
  });

  it('edits only a single selected sensor', async () => {
    const view = await render({ isAdmin: true, writeExperimentIds: [] });

    await view.select(row('sensor-1', 'p1'), row('sensor-1', 'p2'));
    expect(view.button('Edit Sensor')?.disabled).toBe(false);

    await view.select(row('sensor-1'), row('sensor-2'));
    expect(view.button('Edit Sensor')?.disabled).toBe(true);
  });
});

class TableView {
  readonly element: HTMLElement;

  constructor(
    readonly fixture: ComponentFixture<SensorTable>,
    private readonly api: Record<string, ReturnType<typeof vi.fn>>,
  ) {
    this.element = fixture.nativeElement as HTMLElement;
  }

  button(label: string): HTMLButtonElement | undefined {
    return [...this.element.querySelectorAll<HTMLButtonElement>('app-table-header button')].find(
      (button) => button.textContent?.includes(label),
    );
  }

  async gridReady(): Promise<void> {
    await vi.waitFor(() => expect(this.api['getSensors']).toHaveBeenCalled());
  }

  async settle(): Promise<void> {
    await this.fixture.whenStable();
  }

  async select(...rows: SensorParameterRowResponseDto[]): Promise<void> {
    this.fixture.componentInstance.onSelectionChanged(rows);
    await this.fixture.whenStable();
  }
}
