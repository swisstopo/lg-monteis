import { DatePipe } from '@angular/common';
import { inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { ExperimentResponseDto } from '../../../core/generated';
import { TableColumn } from '../../../ui/table/table.types';
import StatusEnum = ExperimentResponseDto.StatusEnum;

export function createColumns(datePipe: DatePipe): TableColumn<ExperimentResponseDto>[] {
  const translateService = inject(TranslateService);

  return [
    {
      field: 'name',
      headerName: translateService.translate('experiment.name.label')(),
      sortable: true,
      filter: 'agTextColumnFilter',
    },
    {
      field: 'status',
      headerName: translateService.translate('experiment.status.label')(),
      sortable: false,
      filter: false,
      valueFormatter: (params) => {
        switch (params.value) {
          case StatusEnum.Active:
            return translateService.translate('experiment.status.option.active.label')();
          case StatusEnum.Historic:
            return translateService.translate('experiment.status.option.historic.label')();
          case StatusEnum.Upcoming:
            return translateService.translate('experiment.status.option.upcoming.label')();
          default:
            return params.value ?? '';
        }
      },
    },
    {
      field: 'period.start',
      headerName: translateService.translate('experiment.period.start.label')(),
      sortable: true,
      filter: true,
      valueFormatter: (params) => datePipe.transform(params.value) ?? '',
    },
    {
      field: 'period.end',
      headerName: translateService.translate('experiment.period.end.label')(),
      sortable: true,
      filter: true,
      valueFormatter: (params) => datePipe.transform(params.value) ?? '',
    },
    {
      field: 'sensorCount',
      headerName: translateService.translate('experiment.sensorCount.label')(),
      sortable: false,
      filter: false,
    },
    {
      field: 'comment',
      headerName: translateService.translate('experiment.comment.label')(),
      sortable: true,
      filter: 'agTextColumnFilter',
      wrapText: true,
      autoHeight: true,
      flex: 0,
      width: 500,
      suppressAutoSize: true,
    },
  ];
}
