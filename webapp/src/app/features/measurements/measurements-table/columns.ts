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
      flex: 1.5,
    },
    {
      field: 'dasKey',
      headerName: translateService.translate('measurements-table.column.dasKey')(),
      sortable: true,
      filter: 'agTextColumnFilter',
      flex: 1.5,
    },
    {
      field: 'sensorName',
      headerName: translateService.translate('measurements-table.column.sensorName')(),
      sortable: true,
      filter: 'agTextColumnFilter',
      flex: 1.5,
    },
    {
      field: 'sensorParameterName',
      headerName: translateService.translate('measurements-table.column.sensorParameterName')(),
      sortable: true,
      filter: 'agTextColumnFilter',
      flex: 1.5,
    },
    {
      field: 'sensorType',
      headerName: translateService.translate('measurements-table.column.sensorType')(),
      sortable: true,
      filter: 'agTextColumnFilter',
      flex: 1,
    },
    {
      field: 'newestMeasurement',
      headerName: translateService.translate('measurements-table.column.newestMeasurement')(),
      sortable: true,
      filter: 'agDateColumnFilter',
      flex: 1.5,
      valueFormatter: (params) =>
        params.value ? (datePipe.transform(params.value, 'medium') ?? '') : '-',
    },
    {
      field: 'measureValue',
      headerName: translateService.translate('measurements-table.column.measureValue')(),
      sortable: true,
      filter: 'agNumberColumnFilter',
      flex: 1,
      valueFormatter: (params) => (params.value != null ? Number(params.value).toFixed(2) : '-'),
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
      flex: 1,
    },
    {
      field: 'trend',
      headerName: translateService.translate('measurements-table.column.trend')(),
      sortable: false,
      filter: false,
      flex: 1.5,
      cellRenderer: TrendCellRenderer,
    },
    {
      field: 'alarmLimits.lower',
      headerName: translateService.translate('sensor.alarmLimit.from.label')(),
      sortable: true,
      filter: 'agNumberColumnFilter',
      flex: 1,
    },
    {
      field: 'alarmLimits.upper',
      headerName: translateService.translate('sensor.alarmLimit.to.label')(),
      sortable: true,
      filter: 'agNumberColumnFilter',
      flex: 1,
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
      flex: 0.5,
    },
    {
      field: 'coordinates.x',
      headerName: translateService.translate('sensor.coordinate.xLocal.label')(),
      sortable: true,
      filter: 'agNumberColumnFilter',
      flex: 1,
    },
    {
      field: 'coordinates.y',
      headerName: translateService.translate('sensor.coordinate.yLocal.label')(),
      sortable: true,
      filter: 'agNumberColumnFilter',
      flex: 1,
    },
    {
      field: 'coordinates.z',
      headerName: translateService.translate('sensor.coordinate.zLocal.label')(),
      sortable: true,
      filter: 'agNumberColumnFilter',
      flex: 1,
    },
    {
      field: 'comment',
      headerName: translateService.translate('measurements-table.column.comment')(),
      sortable: true,
      filter: 'agTextColumnFilter',
      flex: 2,
    },
  ];
}
