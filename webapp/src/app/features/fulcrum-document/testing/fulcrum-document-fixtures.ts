import {
  FulcrumDocumentResponseDto,
  FulcrumEntryResponseDto,
  FulcrumSyncStatusResponseDto,
} from '@core/generated';

export function fulcrumDocument(
  overrides: Partial<FulcrumDocumentResponseDto> = {},
): FulcrumDocumentResponseDto {
  return {
    id: 'document-1',
    fileName: 'report.pdf',
    contentType: 'application/pdf',
    sizeBytes: 1024,
    viewable: true,
    ...overrides,
  };
}

export function fulcrumEntry(
  overrides: Partial<FulcrumEntryResponseDto> = {},
): FulcrumEntryResponseDto {
  return {
    id: 'entry-1',
    number: 'TR 2019-04',
    title: 'Self-sealing of fractures in Opalinus Clay',
    authors: 'Bossart, P.; Thury, M.',
    year: 2019,
    category: 'Technical Report',
    keywords: ['hydrogeology'],
    availableInFulcrum: true,
    document: fulcrumDocument(),
    ...overrides,
  };
}

export function syncStatus(
  overrides: Partial<FulcrumSyncStatusResponseDto> = {},
): FulcrumSyncStatusResponseDto {
  return { running: false, lastSyncedAt: '2026-10-15T08:03:12Z', ...overrides };
}
