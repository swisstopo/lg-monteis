import { DatePipe } from '@angular/common';
import { inject } from '@angular/core';
import { ExperimentResponseDto } from '@core/generated';
import { TranslateService } from '@ngx-translate/core';
import { CopyCellRenderer } from '@ui/table/copy-cell-renderer/copy-cell-renderer';
import { TableColumn } from '@ui/table/table.types';
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
      // Variable/auto row height isn't supported by the infinite row model this table uses (a
      // long comment would render past the fixed row height and bleed into the row below), so
      // this stays single-line and truncates with an ellipsis; the full text is available via
      // the tooltip.
      tooltipField: 'comment',
      flex: 0,
      width: 450,
      suppressAutoSize: true,
    },
    {
      field: 'id',
      headerName: translateService.translate('experiment.id.label')(),
      sortable: false,
      filter: false,
      cellRenderer: CopyCellRenderer,
      flex: 0,
      width: 380,
      suppressAutoSize: true,
    },
  ];
}
