import { TestBed } from '@angular/core/testing';
import { provideNativeDateAdapter } from '@angular/material/core';
import { ActivatedRoute, convertToParamMap, Params } from '@angular/router';
import { MeasurementControllerService, MeasurementResponseDto } from '@core/generated';
import { provideTranslateService } from '@ngx-translate/core';
import { WorkbenchRouter, WorkbenchView } from '@scion/workbench';
import { AllCommunityModule, ModuleRegistry } from 'ag-grid-community';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import MeasurementsTable from './measurements-table';

// registered in main.ts, test bed never runs it
ModuleRegistry.registerModules([AllCommunityModule]);

const DAS_KEY_FILTER = { dasKey: { filterType: 'text', type: 'equals', filter: 'DAS-1' } };

describe('MeasurementsTable', () => {
  let measurementApi: {
    getMeasurements: ReturnType<typeof vi.fn>;
    getChartData: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    measurementApi = {
      getMeasurements: vi.fn(() => of({ rows: [], totalCount: 0 })),
      getChartData: vi.fn(() => of([])),
    };
  });

  function configure(params: Params = {}): void {
    TestBed.configureTestingModule({
      imports: [MeasurementsTable],
      providers: [
        { provide: MeasurementControllerService, useValue: measurementApi },
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap(params)) } },
        { provide: WorkbenchView, useValue: { id: 'view.3', title: undefined } },
        { provide: WorkbenchRouter, useValue: { navigate: vi.fn() } },
        provideTranslateService(),
        provideNativeDateAdapter(),
      ],
    });
  }

  async function requestedFilterModels(params?: Params): Promise<unknown[]> {
    configure(params);
    const fixture = TestBed.createComponent(MeasurementsTable);
    await fixture.whenStable();
    await vi.waitFor(() => expect(measurementApi.getMeasurements).toHaveBeenCalled());
    await fixture.whenStable();
    // getMeasurements(startRow, endRow, sortModel, filterModel)
    return measurementApi.getMeasurements.mock.calls.map(([, , , filterModel]) =>
      filterModel ? JSON.parse(filterModel as string) : undefined,
    );
  }

  it('loads the measurements once and unfiltered without a filter param', async () => {
    expect(await requestedFilterModels()).toEqual([undefined]);
  });

  it('loads the measurements once and filtered to the filter param', async () => {
    const filterModels = await requestedFilterModels({ filter: JSON.stringify(DAS_KEY_FILTER) });

    expect(filterModels).toEqual([DAS_KEY_FILTER]);
  });

  it('derives distinct, defined sensor ids from the selected rows only', () => {
    configure();
    const component = TestBed.createComponent(MeasurementsTable).componentInstance as unknown as {
      selectedRows: { set: (rows: MeasurementResponseDto[]) => void };
      selectedSensorIds: () => string[];
    };

    component.selectedRows.set([
      { sensorParameterId: '1' },
      { sensorParameterId: '2' },
      { sensorParameterId: '2' },
      { sensorParameterId: undefined },
    ]);

    expect(component.selectedSensorIds()).toEqual(['1', '2']);
  });
});
