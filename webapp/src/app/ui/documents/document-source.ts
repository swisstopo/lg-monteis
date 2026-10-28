import { DocumentResponseDto } from '@core/generated';

// each owner provides its own where the list is shown: { provide: DocumentSource, useExisting: … }
export abstract class DocumentSource {
  abstract list(ownerId: string): Promise<DocumentResponseDto[]>;

  abstract upload(ownerId: string, file: File): Promise<DocumentResponseDto>;

  abstract content(ownerId: string, documentId: string): Promise<Blob>;
}
