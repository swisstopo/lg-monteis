import { inject } from '@angular/core';
import { SensorParameterResponseDto, SensorParameterRowResponseDto } from '@core/generated';
import { getDasMetadata, getUnitMetadata } from '@features/sensor/models/sensor.model';
import { TranslateService } from '@ngx-translate/core';
import { TableColumn } from '@ui/table/table.types';

export function createColumns(): TableColumn<SensorParameterRowResponseDto>[] {
  const translateService = inject(TranslateService);
  const dasMetadata = getDasMetadata();
  const unitMetadata = getUnitMetadata();

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
      // Not text-filterable: the backend column is a DB-level enum, not a plain string.
      filter: false,
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
      filter: false,
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
      // Not filterable: a boolean Yes/No column would need agSetColumnFilter, a different
      // filter-model shape the backend doesn't translate yet.
      filter: false,
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
      // Not text-filterable: same enum-column reasoning as the sensor-level `das` column above.
      filter: false,
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
      field: 'parameter.alarmLimits',
      headerName: translateService.translate('sensor.alarmLimit.column')(),
      // A compound lower/upper display, not a single backend-sortable/filterable field.
      sortable: false,
      filter: false,
      valueFormatter: (params) =>
        params.value
          ? translateService.translate('sensor.alarmLimit.display', {
              lower: params.value.lower,
              upper: params.value.upper,
            })()
          : '',
    },
    {
      field: 'parameter.active',
      headerName: translateService.translate('sensor.parameter.active.label')(),
      sortable: true,
      filter: false,
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
