import { inject } from '@angular/core';
import { FulcrumDocumentResponseDto } from '@core/generated';
import { FulcrumDocumentActions } from '@features/fulcrum-document/fulcrum-document-actions/fulcrum-document-actions';
import { TranslateService } from '@ngx-translate/core';
import { TableColumn } from '@ui/table/table.types';

export function createColumns(): TableColumn<FulcrumDocumentResponseDto>[] {
  const translateService = inject(TranslateService);

  return [
    {
      field: 'number',
      headerName: translateService.translate('fulcrumDocument.number.label')(),
      sortable: true,
      filter: 'agTextColumnFilter',
      width: 140,
    },
    {
      field: 'title',
      headerName: translateService.translate('fulcrumDocument.title.label')(),
      sortable: true,
      filter: 'agTextColumnFilter',
      tooltipField: 'title',
      flex: 3,
      minWidth: 240,
    },
    {
      field: 'authors',
      headerName: translateService.translate('fulcrumDocument.authors.label')(),
      sortable: true,
      filter: 'agTextColumnFilter',
      tooltipField: 'authors',
      flex: 2,
      minWidth: 180,
    },
    {
      field: 'year',
      headerName: translateService.translate('fulcrumDocument.year.label')(),
      sortable: true,
      filter: 'agNumberColumnFilter',
      width: 120,
    },
    {
      field: 'category',
      headerName: translateService.translate('fulcrumDocument.category.label')(),
      sortable: true,
      filter: 'agTextColumnFilter',
      width: 180,
    },
    {
      colId: 'file',
      headerName: translateService.translate('fulcrumDocument.file.label')(),
      sortable: false,
      filter: false,
      cellRenderer: FulcrumDocumentActions,
      width: 120,
    },
  ];
}
