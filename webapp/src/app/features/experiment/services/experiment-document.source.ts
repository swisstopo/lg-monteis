import { Injectable, inject } from '@angular/core';
import { DocumentResponseDto, ExperimentDocumentControllerService } from '@core/generated';
import { skipGlobalErrorToast } from '@core/http/http-context';
import { DocumentSource } from '@ui/documents/document-source';
import { firstValueFrom } from 'rxjs';

/**
 * Upload, content and delete requests skip the global error toast, the documents section toasts
 * them.
 */
@Injectable({ providedIn: 'root' })
export class ExperimentDocumentSource extends DocumentSource {
  private readonly api = inject(ExperimentDocumentControllerService);

  list(experimentId: string): Promise<DocumentResponseDto[]> {
    return firstValueFrom(this.api.getDocuments(experimentId));
  }

  upload(experimentId: string, file: File): Promise<DocumentResponseDto> {
    return firstValueFrom(
      this.api.uploadDocument(experimentId, file, 'body', false, {
        context: skipGlobalErrorToast(),
      }),
    );
  }

  fetchContent(experimentId: string, documentId: string): Promise<Blob> {
    return firstValueFrom(
      this.api.downloadDocument(experimentId, documentId, 'body', false, {
        context: skipGlobalErrorToast(),
      }),
    );
  }

  async delete(experimentId: string, documentId: string): Promise<void> {
    await firstValueFrom(
      this.api.deleteDocument(experimentId, documentId, 'body', false, {
        context: skipGlobalErrorToast(),
      }),
    );
  }
}
