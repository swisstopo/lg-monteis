import { HttpContext } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { OrganisationControllerService, WriteOrganisationDto } from '@core/generated';
import { SKIP_GLOBAL_ERROR_TOAST } from '@core/http/http-context';
import { IGetRowsParams } from 'ag-grid-community';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { OrganisationService } from './organisation.service';

const NEW_ORGANISATION: WriteOrganisationDto = { name: 'swisstopo', comment: 'Operator' };
const SAVED = { id: 'organisation-1', name: 'swisstopo', comment: 'Operator' };

describe('OrganisationService', () => {
  let api: Record<string, ReturnType<typeof vi.fn>>;
  let service: OrganisationService;

  beforeEach(() => {
    api = {
      getAllOrganisations: vi.fn(() => of([])),
      getOrganisation: vi.fn(() => of(SAVED)),
      createOrganisation: vi.fn(() => of(SAVED)),
      updateOrganisation: vi.fn(() => of(SAVED)),
      getOrganisations: vi.fn(() => of({ rows: [SAVED], totalCount: 1 })),
      getOrganisationsCsv: vi.fn(() => of(new Blob(['name']))),
    };
    TestBed.configureTestingModule({
      providers: [{ provide: OrganisationControllerService, useValue: api }],
    });
    service = TestBed.inject(OrganisationService);
  });

  it('creates an organisation and flags the table and the organisation list', async () => {
    // a reload while the first load is still running is dropped
    await vi.waitFor(() => expect(service['loadedOrganisations'].status()).toBe('resolved'));
    const loadsBefore = api['getAllOrganisations'].mock.calls.length;

    await expect(service.createOrganisation(NEW_ORGANISATION)).resolves.toEqual(SAVED);
    TestBed.tick();

    expect(api['createOrganisation']).toHaveBeenCalledWith(NEW_ORGANISATION);
    expect(service.organisationsChanged()).toBe(true);
    await vi.waitFor(() =>
      expect(api['getAllOrganisations'].mock.calls.length).toBeGreaterThan(loadsBefore),
    );
  });

  it('lists no organisations when loading them failed', async () => {
    api['getAllOrganisations'].mockReturnValue(throwError(() => new Error('500')));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [{ provide: OrganisationControllerService, useValue: api }],
    });
    service = TestBed.inject(OrganisationService);

    await vi.waitFor(() => expect(service['loadedOrganisations'].status()).toBe('error'));

    expect(service.allOrganisations()).toEqual([]);
  });

  it('updates an organisation and flags the table', async () => {
    await expect(service.updateOrganisation('organisation-1', NEW_ORGANISATION)).resolves.toEqual(
      SAVED,
    );

    expect(api['updateOrganisation']).toHaveBeenCalledWith('organisation-1', NEW_ORGANISATION);
    expect(service.organisationsChanged()).toBe(true);
  });

  it('rethrows a failed create, keeps its error and leaves the table alone', async () => {
    const failure = new Error('409');
    api['createOrganisation'].mockReturnValue(throwError(() => failure));

    await expect(service.createOrganisation(NEW_ORGANISATION)).rejects.toBe(failure);

    expect(service.error()).toBeDefined();
    expect(service.organisationsChanged()).toBe(false);
  });

  it('rethrows a failed update, keeps its error and leaves the table alone', async () => {
    const failure = new Error('409');
    api['updateOrganisation'].mockReturnValue(throwError(() => failure));

    await expect(service.updateOrganisation('organisation-1', NEW_ORGANISATION)).rejects.toBe(
      failure,
    );

    expect(service.error()).toBeDefined();
    expect(service.organisationsChanged()).toBe(false);
  });

  it('pages the organisations with the sort and filter of the grid', async () => {
    const params = {
      startRow: 0,
      endRow: 100,
      sortModel: [{ colId: 'name', sort: 'asc' }],
      filterModel: { name: { filterType: 'text', type: 'contains', filter: 'swiss' } },
    } as unknown as IGetRowsParams;

    await expect(service.getOrganisations(params)).resolves.toEqual({
      rows: [SAVED],
      totalCount: 1,
    });

    expect(api['getOrganisations']).toHaveBeenCalledWith(
      0,
      100,
      JSON.stringify(params.sortModel),
      JSON.stringify(params.filterModel),
    );
  });

  it('exports the CSV without the global error toast, the table toasts it itself', async () => {
    await service.getOrganisationsCsv('[]', '{}');

    const [sortModel, filterModel, , , options] = api['getOrganisationsCsv'].mock.calls[0];
    expect([sortModel, filterModel]).toEqual(['[]', '{}']);
    expect((options.context as HttpContext).get(SKIP_GLOBAL_ERROR_TOAST)).toBe(true);
  });
});
