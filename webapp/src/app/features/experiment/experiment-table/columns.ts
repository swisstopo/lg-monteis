import { DatePipe } from '@angular/common';
import { inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { ExperimentResponseDto } from '../../../core/generated';
import { TableColumn } from '../../../ui/table/table.types';
import StatusEnum = ExperimentResponseDto.StatusEnum;

// Only a single filter condition per column is supported by the paged backend endpoint, so
// combined ("AND"/"OR") filters are disabled here to keep the filter model ag-grid sends in sync
// with what the backend can translate.
const SINGLE_CONDITION_FILTER_PARAMS = { maxNumConditions: 1 };

export function createColumns(datePipe: DatePipe): TableColumn<ExperimentResponseDto>[] {
  const i18n = inject(TranslateService);

  return [
    {
      field: 'name',
      headerName: i18n.translate('experiment.name.label')(),
      sortable: true,
      filter: 'agTextColumnFilter',
      filterParams: SINGLE_CONDITION_FILTER_PARAMS,
    },
    {
      field: 'status',
      headerName: i18n.translate('experiment.status.label')(),
      sortable: false,
      filter: false,
      valueFormatter: (params) => {
        switch (params.value) {
          case StatusEnum.Active:
            return i18n.translate('experiment.status.option.active.label')();
          case StatusEnum.Historic:
            return i18n.translate('experiment.status.option.historic.label')();
          case StatusEnum.Upcoming:
            return i18n.translate('experiment.status.option.upcoming.label')();
          default:
            return params.value ?? '';
        }
      },
    },
    {
      field: 'period.start',
      headerName: i18n.translate('experiment.period.start.label')(),
      sortable: true,
      filter: true,
      filterParams: SINGLE_CONDITION_FILTER_PARAMS,
      valueFormatter: (params) => datePipe.transform(params.value) ?? '',
    },
    {
      field: 'period.end',
      headerName: i18n.translate('experiment.period.end.label')(),
      sortable: true,
      filter: true,
      filterParams: SINGLE_CONDITION_FILTER_PARAMS,
      valueFormatter: (params) => datePipe.transform(params.value) ?? '',
    },
    {
      field: 'sensorCount',
      headerName: i18n.translate('experiment.sensorCount.label')(),
      sortable: false,
      filter: false,
      filterParams: SINGLE_CONDITION_FILTER_PARAMS,
    },
    {
      field: 'comment',
      headerName: i18n.translate('experiment.comment.label')(),
      sortable: true,
      filter: 'agTextColumnFilter',
      filterParams: SINGLE_CONDITION_FILTER_PARAMS,
      wrapText: true,
      autoHeight: true,
      flex: 0,
      width: 500,
      suppressAutoSize: true,
    },
  ];
}
