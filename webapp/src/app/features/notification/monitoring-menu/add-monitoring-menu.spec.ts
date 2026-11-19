import { TestBed } from '@angular/core/testing';
import { ExperimentControllerService, ExperimentResponseDto } from '@core/generated';
import { WorkbenchLayout } from '@scion/workbench';
import { Observable, of, throwError } from 'rxjs';
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

function withWritableExperiments(experiments: Observable<Partial<ExperimentResponseDto>[]>) {
  const api = { getAllExperiments: vi.fn(() => experiments) };
  TestBed.configureTestingModule({
    providers: [{ provide: ExperimentControllerService, useValue: api }],
  });
  return api;
}

function addTo(layout: ReturnType<typeof fakeLayout>) {
  return TestBed.runInInjectionContext(() =>
    addMonitoringMenu(layout as unknown as WorkbenchLayout),
  );
}

describe('addMonitoringMenu', () => {
  it('asks for the experiments the user may write', async () => {
    const api = withWritableExperiments(of([]));

    await addTo(fakeLayout());

    expect(api.getAllExperiments).toHaveBeenCalledWith('WRITE');
  });

  it('adds the monitoring menu part when the user may write an experiment', async () => {
    withWritableExperiments(of([{ id: 'g', name: 'Gas Path' }]));
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
    withWritableExperiments(of([]));
    const layout = fakeLayout();

    await addTo(layout);

    expect(layout.addPart).not.toHaveBeenCalled();
  });

  it('leaves the part out and logs when the experiments fail to load', async () => {
    const failure = new Error('offline');
    withWritableExperiments(throwError(() => failure));
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const layout = fakeLayout();

    const result = await addTo(layout);

    expect(result).toBe(layout);
    expect(layout.addPart).not.toHaveBeenCalled();
    expect(consoleError).toHaveBeenCalledWith(expect.any(String), failure);
    consoleError.mockRestore();
  });
});
