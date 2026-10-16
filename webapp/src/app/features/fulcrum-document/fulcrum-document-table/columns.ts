import { inject } from '@angular/core';
import { FulcrumEntryResponseDto } from '@core/generated';
import { FulcrumEntryDocuments } from '@features/fulcrum-document/fulcrum-entry-documents/fulcrum-entry-documents';
import { TranslateService } from '@ngx-translate/core';
import { TableColumn } from '@ui/table/table.types';

export function createColumns(): TableColumn<FulcrumEntryResponseDto>[] {
  const translateService = inject(TranslateService);

  return [
    {
      field: 'number',
      headerName: translateService.translate('fulcrumEntry.number.label')(),
      sortable: true,
      filter: 'agTextColumnFilter',
      width: 140,
    },
    {
      field: 'title',
      headerName: translateService.translate('fulcrumEntry.title.label')(),
      sortable: true,
      filter: 'agTextColumnFilter',
      tooltipField: 'title',
      flex: 3,
      minWidth: 240,
    },
    {
      field: 'authors',
      headerName: translateService.translate('fulcrumEntry.authors.label')(),
      sortable: true,
      filter: 'agTextColumnFilter',
      tooltipField: 'authors',
      flex: 2,
      minWidth: 180,
    },
    {
      field: 'year',
      headerName: translateService.translate('fulcrumEntry.year.label')(),
      sortable: true,
      filter: 'agNumberColumnFilter',
      width: 120,
    },
    {
      field: 'category',
      headerName: translateService.translate('fulcrumEntry.category.label')(),
      sortable: true,
      filter: 'agTextColumnFilter',
      width: 180,
    },
    {
      colId: 'documents',
      headerName: translateService.translate('fulcrumEntry.documents.label')(),
      sortable: false,
      filter: false,
      cellRenderer: FulcrumEntryDocuments,
      width: 140,
    },
  ];
}
