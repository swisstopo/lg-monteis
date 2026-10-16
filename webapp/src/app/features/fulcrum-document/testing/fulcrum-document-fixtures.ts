import {
  FulcrumDocumentResponseDto,
  FulcrumEntryResponseDto,
  FulcrumSyncRunResponseDto,
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
    availableInFulcrum: true,
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
    documents: [fulcrumDocument()],
    ...overrides,
  };
}

export function syncRun(
  overrides: Partial<FulcrumSyncRunResponseDto> = {},
): FulcrumSyncRunResponseDto {
  return {
    id: 'run-1',
    status: 'SUCCEEDED',
    startedAt: '2026-10-15T08:00:00Z',
    finishedAt: '2026-10-15T08:03:12Z',
    triggeredBy: 'alice',
    ...overrides,
  };
}
