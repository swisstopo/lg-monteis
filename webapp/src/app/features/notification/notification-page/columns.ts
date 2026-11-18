import { DatePipe } from '@angular/common';
import { inject } from '@angular/core';
import { NotifyingParameterResponseDto } from '@core/generated';
import { measurementStateClass } from '@features/measurements/models/measurement-state';
import { sensorParameterLabel } from '@features/measurements/models/sensor-parameter-label';
import { getUnitMetadata, Unit } from '@features/sensor/models/sensor.model';
import { TranslateService } from '@ngx-translate/core';
import { TableColumn } from '@ui/table/table.types';
import { OpenDetailsCellRenderer } from './open-details-cell-renderer';
import { SensorParameterCellRenderer } from './sensor-parameter-cell-renderer';

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
    {
      colId: 'details',
      headerName: translateService.translate('notification.parameters.column.details')(),
      cellRenderer: OpenDetailsCellRenderer,
      sortable: false,
      filter: false,
      floatingFilter: false,
      flex: 1,
    },
  ];
}
