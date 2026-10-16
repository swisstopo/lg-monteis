import {
  FulcrumDocumentFileResponseDto,
  FulcrumDocumentResponseDto,
  FulcrumSyncStatusResponseDto,
} from '@core/generated';

export function fulcrumDocumentFile(
  overrides: Partial<FulcrumDocumentFileResponseDto> = {},
): FulcrumDocumentFileResponseDto {
  return {
    fileName: 'report.pdf',
    contentType: 'application/pdf',
    sizeBytes: 1024,
    viewable: true,
    ...overrides,
  };
}

export function fulcrumDocument(
  overrides: Partial<FulcrumDocumentResponseDto> = {},
): FulcrumDocumentResponseDto {
  return {
    id: 'document-1',
    number: 'TR 2019-04',
    title: 'Self-sealing of fractures in Opalinus Clay',
    authors: 'Bossart, P.; Thury, M.',
    year: 2019,
    category: 'Technical Report',
    keywords: ['hydrogeology'],
    availableInFulcrum: true,
    file: fulcrumDocumentFile(),
    ...overrides,
  };
}

export function syncStatus(
  overrides: Partial<FulcrumSyncStatusResponseDto> = {},
): FulcrumSyncStatusResponseDto {
  return { running: false, lastSuccessfulSyncAt: '2026-10-15T08:03:12Z', ...overrides };
}
