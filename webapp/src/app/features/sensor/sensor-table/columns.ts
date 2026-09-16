import { inject } from '@angular/core';
import {
  ExperimentControllerService,
  SensorParameterResponseDto,
  SensorParameterRowResponseDto,
} from '@core/generated';
import { getDasMetadata, getUnitMetadata, Unit } from '@features/sensor/models/sensor.model';
import { TranslateService } from '@ngx-translate/core';
import { MultiSelectFilter } from '@ui/filters/multi-select-filter/multi-select-filter';
import { TableColumn } from '@ui/table/table.types';
import { firstValueFrom } from 'rxjs';

export function createColumns(): TableColumn<SensorParameterRowResponseDto>[] {
  const translateService = inject(TranslateService);
  const experimentApi = inject(ExperimentControllerService);
  const dasMetadata = getDasMetadata();
  const unitMetadata = getUnitMetadata();

  const booleanFilterOptions = () =>
    Promise.resolve([
      { displayName: translateService.translate('sensor.active.column.yes')(), value: 'true' },
      { displayName: translateService.translate('sensor.active.column.no')(), value: 'false' },
    ]);

  return [
    {
      field: 'dasSensorAlias',
      headerName: translateService.translate('sensor.dasSensorAlias.label')(),
      sortable: true,
      filter: 'agTextColumnFilter',
    },
    {
      field: 'name',
      headerName: translateService.translate('sensor.name.label')(),
      sortable: true,
      filter: 'agTextColumnFilter',
    },
    {
      field: 'das',
      headerName: translateService.translate('sensor.das.label')(),
      sortable: true,
      filter: MultiSelectFilter,
      filterParams: {
        valuesProvider: () =>
          Promise.resolve(
            Object.entries(dasMetadata).map(([value, meta]) => ({
              value,
              displayName: meta.label(),
            })),
          ),
      },
      valueFormatter: (params) =>
        dasMetadata[params.value as SensorParameterRowResponseDto.DasEnum]?.label() ?? '',
    },
    {
      field: 'fulcrumId',
      headerName: translateService.translate('sensor.fulcrumId.label')(),
      sortable: true,
      filter: 'agTextColumnFilter',
    },
    {
      field: 'mainExperiment.name',
      headerName: translateService.translate('sensor.mainExperiment.label')(),
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
      field: 'active',
      headerName: translateService.translate('sensor.active.label')(),
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
      field: 'comment',
      headerName: translateService.translate('sensor.comment.label')(),
      sortable: true,
      filter: 'agTextColumnFilter',
      // Variable/auto row height isn't supported by the infinite row model this table uses (a
      // long comment would render past the fixed row height and bleed into the row below), so
      // this stays single-line and truncates with an ellipsis; the full text is available via
      // the tooltip.
      tooltipField: 'comment',
      flex: 0,
      width: 500,
      suppressAutoSize: true,
    },

    // --- Parameter-level columns: one value per row.
    {
      field: 'parameter.name',
      headerName: translateService.translate('sensor.parameter.name.label')(),
      sortable: true,
      filter: 'agTextColumnFilter',
    },
    {
      field: 'parameter.dasParameterAlias',
      headerName: translateService.translate('sensor.parameter.dasParameterAlias.label')(),
      sortable: true,
      filter: 'agTextColumnFilter',
    },
    {
      field: 'parameter.type.name',
      headerName: translateService.translate('sensor.type.label')(),
      sortable: true,
      filter: 'agTextColumnFilter',
    },
    {
      field: 'parameter.unit',
      headerName: translateService.translate('sensor.unit.label')(),
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
      valueFormatter: (params) =>
        unitMetadata[params.value as SensorParameterResponseDto.UnitEnum]?.symbol() ?? '',
    },
    {
      field: 'parameter.formula.expression',
      headerName: translateService.translate('sensor.formula.column')(),
      sortable: true,
      filter: 'agTextColumnFilter',
    },
    {
      field: 'parameter.alarmLimits.lower',
      headerName: translateService.translate('sensor.alarmLimit.from.label')(),
      sortable: true,
      filter: 'agNumberColumnFilter',
    },
    {
      field: 'parameter.alarmLimits.upper',
      headerName: translateService.translate('sensor.alarmLimit.to.label')(),
      sortable: true,
      filter: 'agNumberColumnFilter',
    },
    {
      field: 'parameter.active',
      headerName: translateService.translate('sensor.parameter.active.label')(),
      sortable: true,
      filter: MultiSelectFilter,
      filterParams: { valuesProvider: booleanFilterOptions },
      cellDataType: false,
      valueFormatter: (params) =>
        params.value
          ? translateService.translate('sensor.active.column.yes')()
          : translateService.translate('sensor.active.column.no')(),
    },
    {
      field: 'parameter.comment',
      headerName: translateService.translate('sensor.parameter.comment.label')(),
      sortable: true,
      filter: 'agTextColumnFilter',
      tooltipField: 'parameter.comment',
      flex: 0,
      width: 400,
      suppressAutoSize: true,
    },
  ];
}
