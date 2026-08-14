import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { Observable, of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ChartDataResponseDto, MeasurementControllerService } from '../../../core/generated';
import { MesurementsService } from './mesurements.service';

function setup(
  getChartData: (
    id: number,
    rangeFrom: string,
    rangeTo: string,
  ) => Observable<ChartDataResponseDto[]>,
) {
  TestBed.configureTestingModule({
    providers: [
      MesurementsService,
      provideTranslateService(),
      { provide: MeasurementControllerService, useValue: { getChartData } },
    ],
  });
  return TestBed.inject(MesurementsService);
}

describe('MesurementsService', () => {
  it('starts idle without fetching anything', () => {
    const getChartData = vi.fn().mockReturnValue(of([]));
    const service = setup(getChartData);

    expect(service.chartData.value()).toBeUndefined();
    expect(getChartData).not.toHaveBeenCalled();
  });

  it('does not call the API when given an empty list of ids', () => {
    const getChartData = vi.fn().mockReturnValue(of([]));
    const service = setup(getChartData);

    service.getChartData([], '2024-01-01', '2024-01-02');

    expect(getChartData).not.toHaveBeenCalled();
    expect(service.chartData.value()).toBeUndefined();
  });

  it('fetches chart data for the given ids and date range', async () => {
    const getChartData = vi.fn().mockReturnValue(of([]));
    const service = setup(getChartData);

    service.getChartData([1, 2], '2024-01-01', '2024-01-02');

    await vi.waitFor(() => expect(getChartData).toHaveBeenCalledTimes(2));
    expect(getChartData).toHaveBeenCalledWith(1, '2024-01-01', '2024-01-02');
    expect(getChartData).toHaveBeenCalledWith(2, '2024-01-01', '2024-01-02');
  });

  it('maps sensors into datasets and groups y-axes by unit', async () => {
    // Sensors have no data points here so we exercise the id/label/axis-grouping
    // logic without hitting the invalid-data check below (see next test).
    const apiResponses: ChartDataResponseDto[] = [
      {
        id: 1,
        sensorName: 'Temperature',
        sensorCode: 'T1',
        unit: ChartDataResponseDto.UnitEnum.Kelvin,
        data: [],
      },
      {
        id: 2,
        sensorName: 'Distance',
        sensorCode: 'D1',
        unit: ChartDataResponseDto.UnitEnum.Meter,
        data: [],
      },
      {
        id: 3,
        sensorName: 'Temperature 2',
        sensorCode: 'T2',
        unit: ChartDataResponseDto.UnitEnum.Kelvin,
        data: [],
      },
    ];
    const service = setup((id) => of(apiResponses.filter((response) => response.id === id)));

    service.getChartData([1, 2, 3], '2024-01-01', '2024-01-02');

    await vi.waitFor(() => expect(service.chartData.value()).toBeDefined());
    const result = service.chartData.value()!;

    expect(result.datasets).toEqual([
      { id: 1, label: 'T1 [KELVIN]', data: [], yAxisId: 'y' },
      { id: 2, label: 'D1 [METER]', data: [], yAxisId: 'y2' },
      { id: 3, label: 'T2 [KELVIN]', data: [], yAxisId: 'y' },
    ]);
    expect(result.yAxisLabels).toEqual({ y: 'KELVIN', y2: 'METER' });
    expect(service.error()).toBeUndefined();
  });

  it('falls back to defaults when sensor fields are missing', async () => {
    const apiResponses: ChartDataResponseDto[] = [{}];
    const service = setup(() => of(apiResponses));

    service.getChartData([1], '2024-01-01', '2024-01-02');

    await vi.waitFor(() => expect(service.chartData.value()).toBeDefined());
    const result = service.chartData.value()!;

    expect(result.datasets).toEqual([
      { id: 'sensor-0', label: 'unknown []', data: [], yAxisId: 'y' },
    ]);
    expect(result.yAxisLabels).toEqual({ y: '' });
  });

  it('maps data points into { x: parsed timestamp, y: value }', async () => {
    const apiResponses: ChartDataResponseDto[] = [
      {
        id: 1,
        sensorName: 'Temperature',
        sensorCode: 'T1',
        unit: ChartDataResponseDto.UnitEnum.Kelvin,
        data: [
          { timestamp: '2024-01-01T00:00:00Z', value: 10 },
          { timestamp: '2024-01-01T01:00:00Z', value: 12 },
        ],
      },
    ];
    const service = setup(() => of(apiResponses));

    service.getChartData([1], '2024-01-01', '2024-01-02');

    await vi.waitFor(() => expect(service.chartData.value()).toBeDefined());

    expect(service.chartData.value()!.datasets).toEqual([
      {
        id: 1,
        label: 'T1 [KELVIN]',
        data: [
          { x: Date.parse('2024-01-01T00:00:00Z'), y: 10 },
          { x: Date.parse('2024-01-01T01:00:00Z'), y: 12 },
        ],
        yAxisId: 'y',
      },
    ]);
    expect(service.error()).toBeUndefined();
  });

  it('drops malformed data points instead of failing the whole chart', async () => {
    const apiResponses: ChartDataResponseDto[] = [
      {
        id: 1,
        sensorName: 'Temperature',
        sensorCode: 'T1',
        unit: ChartDataResponseDto.UnitEnum.Kelvin,
        data: [
          { timestamp: undefined, value: 10 },
          { timestamp: '2024-01-01T00:00:00Z', value: 12 },
        ],
      },
    ];
    const service = setup(() => of(apiResponses));

    service.getChartData([1], '2024-01-01', '2024-01-02');

    await vi.waitFor(() => expect(service.chartData.value()).toBeDefined());
    expect(service.chartData.value()!.datasets).toEqual([
      {
        id: 1,
        label: 'T1 [KELVIN]',
        data: [{ x: Date.parse('2024-01-01T00:00:00Z'), y: 12 }],
        yAxisId: 'y',
      },
    ]);
    expect(service.error()).toBeUndefined();
  });

  it('surfaces an error when the API call fails', async () => {
    const service = setup(() => throwError(() => new Error('network failure')));

    service.getChartData([1], '2024-01-01', '2024-01-02');

    await vi.waitFor(() => expect(service.chartData.status()).toBe('error'));
    expect(service.chartData.hasValue()).toBe(false);
  });
});
