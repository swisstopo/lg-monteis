import { Injectable, inject } from '@angular/core';
import { DocumentResponseDto, SensorDocumentControllerService } from '@core/generated';
import { skipGlobalErrorToast } from '@core/http/http-context';
import { DocumentSource } from '@ui/documents/document-source';
import { firstValueFrom } from 'rxjs';

/** Upload and content requests skip the global error toast, the documents section toasts them. */
@Injectable({ providedIn: 'root' })
export class SensorDocumentSource extends DocumentSource {
  private readonly api = inject(SensorDocumentControllerService);

  list(sensorId: string): Promise<DocumentResponseDto[]> {
    return firstValueFrom(this.api.getDocuments(sensorId));
  }

  upload(sensorId: string, file: File): Promise<DocumentResponseDto> {
    return firstValueFrom(
      this.api.uploadDocument(sensorId, file, 'body', false, {
        context: skipGlobalErrorToast(),
      }),
    );
  }

  content(sensorId: string, documentId: string): Promise<Blob> {
    return firstValueFrom(
      this.api.downloadDocument(sensorId, documentId, 'body', false, {
        context: skipGlobalErrorToast(),
      }),
    );
  }
}
