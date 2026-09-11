import { inject } from '@angular/core';
import { SensorResponseDto } from '@core/generated';
import { getDasMetadata } from '@features/sensor/models/sensor.model';
import { TranslateService } from '@ngx-translate/core';
import { TableColumn } from '@ui/table/table.types';

export function createColumns(): TableColumn<SensorResponseDto>[] {
  const translateService = inject(TranslateService);
  const dasMetadata = getDasMetadata();

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
        dasMetadata[params.value as SensorResponseDto.DasEnum]?.label() ?? '',
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
  ];
}
