import { DatePipe } from '@angular/common';
import { inject } from '@angular/core';
import { NotifyingParameterResponseDto } from '@core/generated';
import { getUnitMetadata, Unit } from '@features/sensor/models/sensor.model';
import { TranslateService } from '@ngx-translate/core';
import { TableColumn } from '@ui/table/table.types';
import { SensorParameterCellRenderer } from './sensor-parameter-cell-renderer';
import MeasurementStatusEnum = NotifyingParameterResponseDto.MeasurementStatusEnum;

const ALARM_STATES: readonly MeasurementStatusEnum[] = [
  MeasurementStatusEnum.TooLow,
  MeasurementStatusEnum.TooHigh,
];

export function sensorParameterLabel(parameter: NotifyingParameterResponseDto): string {
  return parameter.sensorParameterName
    ? `${parameter.sensorName} – ${parameter.sensorParameterName}`
    : (parameter.dasKey ?? '');
}

export function createColumns(datePipe: DatePipe): TableColumn<NotifyingParameterResponseDto>[] {
  const translateService = inject(TranslateService);
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
      filter: false,
      flex: 2,
    },
    {
      colId: 'lastMeasurement',
      headerName: translateService.translate('notification.parameters.column.lastMeasurement')(),
      valueGetter: (params) => params.data?.newestMeasurement,
      valueFormatter: (params) => (params.data ? formatLastMeasurement(params.data) : '-'),
      cellClass: (params) =>
        params.data?.measurementStatus && ALARM_STATES.includes(params.data.measurementStatus)
          ? 'measurement-state-alarm'
          : '',
      sortable: true,
      filter: false,
      flex: 2,
    },
    {
      colId: 'details',
      headerName: translateService.translate('notification.parameters.column.details')(),
      sortable: false,
      filter: false,
      flex: 1,
    },
  ];
}
