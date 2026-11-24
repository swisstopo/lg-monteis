import { TestBed } from '@angular/core/testing';
import { PermissionsService } from '@core/auth/permissions.service';
import { ExperimentControllerService } from '@core/generated';
import { WorkbenchLayout } from '@scion/workbench';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { addMonitoringMenu } from './add-monitoring-menu';

// real layout needs a running SCION workbench
function fakeLayout() {
  const layout = {
    addPart: vi.fn(() => layout),
    navigatePart: vi.fn(() => layout),
  };
  return layout;
}

function withWriteAccess(hasAnyExperimentWriteAccess: boolean) {
  const experimentApi = { getAllExperiments: vi.fn() };
  TestBed.configureTestingModule({
    providers: [
      {
        provide: PermissionsService,
        useValue: {
          currentUser$: of({}),
          hasAnyExperimentWriteAccess: () => hasAnyExperimentWriteAccess,
        },
      },
      { provide: ExperimentControllerService, useValue: experimentApi },
    ],
  });
  return experimentApi;
}

function addTo(layout: ReturnType<typeof fakeLayout>) {
  return TestBed.runInInjectionContext(() =>
    addMonitoringMenu(layout as unknown as WorkbenchLayout),
  );
}

describe('addMonitoringMenu', () => {
  it('adds the monitoring menu part when the user may write an experiment', async () => {
    withWriteAccess(true);
    const layout = fakeLayout();

    await addTo(layout);

    expect(layout.addPart).toHaveBeenCalledWith(
      'monitoring-menu',
      { dockTo: 'left-top' },
      { label: '%menu.monitoring', icon: 'notifications' },
    );
    expect(layout.navigatePart).toHaveBeenCalledWith('monitoring-menu', ['monitoring-menu']);
  });

  it('leaves the part out when the user may write no experiment', async () => {
    withWriteAccess(false);
    const layout = fakeLayout();

    const result = await addTo(layout);

    expect(result).toBe(layout);
    expect(layout.addPart).not.toHaveBeenCalled();
  });

  it('leaves loading the experiments to the menu', async () => {
    const experimentApi = withWriteAccess(true);

    await addTo(fakeLayout());

    expect(experimentApi.getAllExperiments).not.toHaveBeenCalled();
  });
});
