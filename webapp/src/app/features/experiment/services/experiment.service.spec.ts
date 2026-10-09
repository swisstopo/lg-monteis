import { HttpContext } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ExperimentControllerService, WriteExperimentDto } from '@core/generated';
import { SKIP_GLOBAL_ERROR_TOAST } from '@core/http/http-context';
import { IGetRowsParams } from 'ag-grid-community';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ExperimentService } from './experiment.service';

const NEW_EXPERIMENT: WriteExperimentDto = {
  name: 'Mont Terri Alpha',
  period: { start: '2030-01-01', end: '2030-05-05' },
};
const SAVED = { id: 'experiment-1', name: 'Mont Terri Alpha', version: 1 };

describe('ExperimentService', () => {
  let api: Record<string, ReturnType<typeof vi.fn>>;
  let service: ExperimentService;

  beforeEach(() => {
    api = {
      getAllExperiments: vi.fn(() => of([])),
      getExperiment: vi.fn(() => of(SAVED)),
      createExperiment: vi.fn(() => of(SAVED)),
      updateExperiment: vi.fn(() => of(SAVED)),
      getExperiments: vi.fn(() => of({ rows: [SAVED], totalCount: 1 })),
      getExperimentsCsv: vi.fn(() => of(new Blob(['name']))),
    };
    TestBed.configureTestingModule({
      providers: [{ provide: ExperimentControllerService, useValue: api }],
    });
    service = TestBed.inject(ExperimentService);
  });

  it('loads one experiment', async () => {
    await expect(service.getExperiment('experiment-1')).resolves.toEqual(SAVED);
    expect(api['getExperiment']).toHaveBeenCalledWith('experiment-1');
  });

  it('creates an experiment without id', async () => {
    await expect(service.saveExperiment(NEW_EXPERIMENT)).resolves.toEqual(SAVED);

    expect(api['createExperiment']).toHaveBeenCalledWith(NEW_EXPERIMENT);
    expect(api['updateExperiment']).not.toHaveBeenCalled();
  });

  it('updates an experiment with id', async () => {
    const existing = { ...NEW_EXPERIMENT, id: 'experiment-1', version: 1 };

    await service.saveExperiment(existing);

    expect(api['updateExperiment']).toHaveBeenCalledWith('experiment-1', existing);
    expect(api['createExperiment']).not.toHaveBeenCalled();
  });

  it('tells the table and the experiment list after a save', async () => {
    // a reload while the first load is still running is dropped
    await vi.waitFor(() => expect(service.allExperiments.status()).toBe('resolved'));
    const loadsBefore = api['getAllExperiments'].mock.calls.length;

    await service.saveExperiment(NEW_EXPERIMENT);
    TestBed.tick();

    expect(service.experimentsSaved()).toBe(1);
    await vi.waitFor(() =>
      expect(api['getAllExperiments'].mock.calls.length).toBeGreaterThan(loadsBefore),
    );
  });

  it('counts every save', async () => {
    await service.saveExperiment(NEW_EXPERIMENT);
    await service.saveExperiment({ ...NEW_EXPERIMENT, id: 'experiment-1', version: 1 });

    expect(service.experimentsSaved()).toBe(2);
  });

  it('rethrows a failed save and leaves the table alone', async () => {
    const failure = new Error('409');
    api['createExperiment'].mockReturnValue(throwError(() => failure));

    await expect(service.saveExperiment(NEW_EXPERIMENT)).rejects.toBe(failure);
    expect(service.experimentsSaved()).toBe(0);
  });

  it('pages the experiments with the sort and filter of the grid', async () => {
    const params = {
      startRow: 0,
      endRow: 100,
      sortModel: [{ colId: 'name', sort: 'asc' }],
      filterModel: { name: { filterType: 'text', type: 'contains', filter: 'Alpha' } },
    } as unknown as IGetRowsParams;

    await expect(service.getExperiments(params)).resolves.toEqual({ rows: [SAVED], totalCount: 1 });

    expect(api['getExperiments']).toHaveBeenCalledWith(
      0,
      100,
      JSON.stringify(params.sortModel),
      JSON.stringify(params.filterModel),
    );
  });

  it('exports the CSV without the global error toast, the table toasts it itself', async () => {
    await service.getExperimentsCsv('[]', '{}');

    const [sortModel, filterModel, , , options] = api['getExperimentsCsv'].mock.calls[0];
    expect([sortModel, filterModel]).toEqual(['[]', '{}']);
    expect((options.context as HttpContext).get(SKIP_GLOBAL_ERROR_TOAST)).toBe(true);
  });
});
