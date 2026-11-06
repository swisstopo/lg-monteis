import { inject } from '@angular/core';
import { OrganisationResponseDto } from '@core/generated';
import { TranslateService } from '@ngx-translate/core';
import { TableColumn } from '@ui/table/table.types';

export function createColumns(): TableColumn<OrganisationResponseDto>[] {
  const translateService = inject(TranslateService);

  return [
    {
      field: 'name',
      headerName: translateService.translate('organisation.name.label')(),
      sortable: true,
      filter: 'agTextColumnFilter',
      flex: 1,
    },
    {
      field: 'comment',
      headerName: translateService.translate('organisation.comment.label')(),
      sortable: true,
      filter: 'agTextColumnFilter',
      tooltip: (params) => params.value,
      flex: 3,
    },
  ];
}
