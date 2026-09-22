import { DatePipe } from '@angular/common';
import { inject } from '@angular/core';
import {
  ExperimentControllerService,
  MeasurementResponseDto,
  SensorParameterResponseDto,
} from '@core/generated';
import { getUnitMetadata, Unit } from '@features/sensor/models/sensor.model';
import { TranslateService } from '@ngx-translate/core';
import { MultiSelectFilter } from '@ui/filters/multi-select-filter/multi-select-filter';
import { TableColumn } from '@ui/table/table.types';
import { firstValueFrom } from 'rxjs';
import { TrendCellRenderer } from './trend-cell-renderer';

export function createColumns(datePipe: DatePipe): TableColumn<MeasurementResponseDto>[] {
  const translateService = inject(TranslateService);
  const experimentApi = inject(ExperimentControllerService);
  const unitMetadata = getUnitMetadata();

  const booleanFilterOptions = () =>
    Promise.resolve([
      { displayName: translateService.translate('sensor.active.column.yes')(), value: 'true' },
      { displayName: translateService.translate('sensor.active.column.no')(), value: 'false' },
    ]);

  const MEASUREMENT_STATE_CLASS: Record<string, string> = {
    correct: '',
    too_low: 'measurement-state-alarm',
    too_high: 'measurement-state-alarm',
  };

  return [
    {
      field: 'experimentName',
      headerName: translateService.translate('measurements-table.column.experimentName')(),
      sortable: true,
      filter: MultiSelectFilter,
      filterParams: {
        valuesProvider: async () => {
          const experiments = await firstValueFrom(experimentApi.getAllExperiments());
          return experiments
            .map((experiment) => experiment.name)
            .filter((name): name is string => !!name)
            .map((name) => ({ displayName: name, value: name }));
        },
        blankOptionLabel: translateService.translate('sensor.mainExperiment.none')(),
      },
    },
    {
      field: 'dasKey',
      headerName: translateService.translate('measurements-table.column.dasKey')(),
      sortable: true,
      filter: 'agTextColumnFilter',
    },
    {
      field: 'sensorName',
      headerName: translateService.translate('measurements-table.column.sensorName')(),
      sortable: true,
      filter: 'agTextColumnFilter',
    },
    {
      field: 'sensorParameterName',
      headerName: translateService.translate('measurements-table.column.sensorParameterName')(),
      sortable: true,
      filter: 'agTextColumnFilter',
    },
    {
      field: 'sensorType',
      headerName: translateService.translate('measurements-table.column.sensorType')(),
      sortable: true,
      filter: 'agTextColumnFilter',
    },
    {
      field: 'newestMeasurement',
      headerName: translateService.translate('measurements-table.column.newestMeasurement')(),
      sortable: true,
      filter: 'agDateColumnFilter',
      valueFormatter: (params) =>
        params.value ? (datePipe.transform(params.value, 'medium') ?? '') : '-',
    },
    {
      field: 'measureValue',
      headerName: translateService.translate('measurements-table.column.measureValue')(),
      sortable: true,
      filter: 'agNumberColumnFilter',
      valueFormatter: (params) => (params.value != null ? Number(params.value).toFixed(2) : '-'),
      cellClass: (params) =>
        params.data?.measurementState
          ? MEASUREMENT_STATE_CLASS[params.data.measurementState.toLowerCase()]
          : '',
    },
    {
      field: 'unit',
      headerName: translateService.translate('measurements-table.column.unit')(),
      sortable: true,
      filter: MultiSelectFilter,
      filterParams: {
        valuesProvider: () =>
          Promise.resolve(
            Object.values(SensorParameterResponseDto.UnitEnum).map((value) => ({
              value,
              displayName: unitMetadata[value as Unit]?.symbol() ?? value,
            })),
          ),
      },
      valueFormatter: (params) => unitMetadata[params.value as Unit]?.symbol() ?? params.value,
    },
    {
      field: 'trend',
      headerName: translateService.translate('measurements-table.column.trend')(),
      sortable: false,
      filter: false,
      cellRenderer: TrendCellRenderer,
    },
    {
      field: 'alarmLimits.lower',
      headerName: translateService.translate('sensor.alarmLimit.from.label')(),
      sortable: true,
      filter: 'agNumberColumnFilter',
    },
    {
      field: 'alarmLimits.upper',
      headerName: translateService.translate('sensor.alarmLimit.to.label')(),
      sortable: true,
      filter: 'agNumberColumnFilter',
    },
    {
      field: 'active',
      headerName: translateService.translate('measurements-table.column.active')(),
      sortable: true,
      filter: MultiSelectFilter,
      filterParams: { valuesProvider: booleanFilterOptions },
      // Without this, ag-grid infers the boolean cell data type from the row data and renders a
      // (valueFormatter-ignoring) checkbox instead of the Yes/No text below.
      cellDataType: false,
      valueFormatter: (params) =>
        params.value
          ? translateService.translate('sensor.active.column.yes')()
          : translateService.translate('sensor.active.column.no')(),
    },
    {
      field: 'coordinates.x',
      headerName: translateService.translate('sensor.coordinate.xLocal.label')(),
      sortable: true,
      filter: 'agNumberColumnFilter',
    },
    {
      field: 'coordinates.y',
      headerName: translateService.translate('sensor.coordinate.yLocal.label')(),
      sortable: true,
      filter: 'agNumberColumnFilter',
    },
    {
      field: 'coordinates.z',
      headerName: translateService.translate('sensor.coordinate.zLocal.label')(),
      sortable: true,
      filter: 'agNumberColumnFilter',
    },
    {
      field: 'comment',
      headerName: translateService.translate('measurements-table.column.comment')(),
      sortable: true,
      filter: 'agTextColumnFilter',
      tooltip: (params) => params.value,
      width: 400,
      suppressAutoSize: true,
    },
  ];
}
