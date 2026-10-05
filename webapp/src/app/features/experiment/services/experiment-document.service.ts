import { Injectable, inject } from '@angular/core';
import {
  ExperimentDocumentControllerService,
  ExperimentDocumentResponseDto,
} from '@core/generated';
import { skipGlobalErrorToast } from '@core/http/http-context';
import { firstValueFrom } from 'rxjs';

/** Upload and content requests skip the global error toast, the documents section toasts them. */
@Injectable({ providedIn: 'root' })
export class ExperimentDocumentService {
  private readonly api = inject(ExperimentDocumentControllerService);

  getDocuments(experimentId: string): Promise<ExperimentDocumentResponseDto[]> {
    return firstValueFrom(this.api.getDocuments(experimentId));
  }

  uploadDocument(experimentId: string, file: File): Promise<ExperimentDocumentResponseDto> {
    return firstValueFrom(
      this.api.uploadDocument(experimentId, file, 'body', false, {
        context: skipGlobalErrorToast(),
      }),
    );
  }

  getContent(experimentId: string, documentId: string): Promise<Blob> {
    return firstValueFrom(
      this.api.downloadDocument(experimentId, documentId, 'body', false, {
        context: skipGlobalErrorToast(),
      }),
    );
  }
}
