import { DocumentResponseDto } from '@core/generated';

// each parent provides its own where the list is shown: { provide: DocumentSource, useExisting: … }
export abstract class DocumentSource {
  abstract list(parentId: string): Promise<DocumentResponseDto[]>;

  abstract upload(parentId: string, file: File): Promise<DocumentResponseDto>;

  abstract content(parentId: string, documentId: string): Promise<Blob>;
}
