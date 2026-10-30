import { inject } from '@angular/core';
import { FulcrumDocumentResponseDto } from '@core/generated';
import { FulcrumDocumentFileName } from '@features/fulcrum-document/fulcrum-document-file-name/fulcrum-document-file-name';
import { TranslateService } from '@ngx-translate/core';
import { TableColumn } from '@ui/table/table.types';

export function createColumns(): TableColumn<FulcrumDocumentResponseDto>[] {
  const translateService = inject(TranslateService);

  return [
    {
      field: 'file.fileName',
      headerName: translateService.translate('fulcrumDocument.file.label')(),
      sortable: true,
      filter: 'agTextColumnFilter',
      cellRenderer: FulcrumDocumentFileName,
      flex: 2,
      minWidth: 200,
    },
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
      tooltip: true,
      flex: 3,
      minWidth: 240,
    },
    {
      field: 'authors',
      headerName: translateService.translate('fulcrumDocument.authors.label')(),
      sortable: true,
      filter: 'agTextColumnFilter',
      tooltip: true,
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
  ];
}
