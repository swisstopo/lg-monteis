import { DatePipe } from '@angular/common';
import { inject } from '@angular/core';
import { NotifyingParameterResponseDto } from '@core/generated';
import {
  dasKeyFilterModel,
  measurementsTableLink,
} from '@features/measurements/measurements-table-link';
import { measurementStateClass } from '@features/measurements/models/measurement-state';
import { getUnitMetadata, Unit } from '@features/sensor/models/sensor.model';
import { TranslateService } from '@ngx-translate/core';
import { WorkbenchRouter } from '@scion/workbench';
import { rowActionsColumn } from '@ui/table/row-actions-cell-renderer/row-actions-cell-renderer';
import { TableColumn } from '@ui/table/table.types';
import { SensorParameterCellRenderer } from './sensor-parameter-cell-renderer';

export function sensorParameterLabel(parameter: NotifyingParameterResponseDto): string {
  return parameter.sensorParameterName
    ? `${parameter.sensorName} – ${parameter.sensorParameterName}`
    : (parameter.dasKey ?? '');
}

export function createColumns(datePipe: DatePipe): TableColumn<NotifyingParameterResponseDto>[] {
  const translateService = inject(TranslateService);
  const workbenchRouter = inject(WorkbenchRouter);
  const unitMetadata = getUnitMetadata();

  const formatLastMeasurement = (parameter: NotifyingParameterResponseDto): string => {
    if (!parameter.newestMeasurement || parameter.measureValue == null) return '-';
    const timestamp = datePipe.transform(parameter.newestMeasurement, 'medium');
    const value = parameter.measureValue.toFixed(2);
    const unit = unitMetadata[parameter.unit as Unit]?.symbol() ?? parameter.unit ?? '';
    return `${timestamp} ${value} ${unit}`.trim();
  };

  return [
    {
      colId: 'sensorParameter',
      headerName: translateService.translate('notification.parameters.column.sensorParameter')(),
      valueGetter: (params) => (params.data ? sensorParameterLabel(params.data) : ''),
      cellRenderer: SensorParameterCellRenderer,
      sortable: true,
      sort: 'asc',
      comparator: (a: string, b: string) => a.localeCompare(b, undefined, { sensitivity: 'base' }),
      filter: false,
      floatingFilter: false,
      flex: 2,
    },
    {
      colId: 'lastMeasurement',
      headerName: translateService.translate('notification.parameters.column.lastMeasurement')(),
      valueGetter: (params) => params.data?.newestMeasurement,
      valueFormatter: (params) => (params.data ? formatLastMeasurement(params.data) : '-'),
      cellClass: (params) => measurementStateClass(params.data?.measurementStatus),
      sortable: true,
      filter: false,
      floatingFilter: false,
      flex: 2,
    },
    rowActionsColumn<NotifyingParameterResponseDto>([
      {
        labelKey: 'notification.parameters.openDetails',
        icon: 'open_in_new',
        disabled: (parameter) => !parameter.dasKey,
        // blank, not auto: auto would re-filter every open measurement table tab
        run: ({ dasKey }) => {
          if (!dasKey) return;
          void workbenchRouter.navigate(measurementsTableLink(dasKeyFilterModel(dasKey)), {
            target: 'blank',
          });
        },
      },
    ]),
  ];
}
