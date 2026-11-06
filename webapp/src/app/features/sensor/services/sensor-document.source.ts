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
    return firstValueFrom(this.api.getSensorDocuments(sensorId));
  }

  upload(sensorId: string, file: File): Promise<DocumentResponseDto> {
    return firstValueFrom(
      this.api.uploadSensorDocument(sensorId, file, 'body', false, {
        context: skipGlobalErrorToast(),
      }),
    );
  }

  fetchContent(sensorId: string, documentId: string): Promise<Blob> {
    return firstValueFrom(
      this.api.downloadSensorDocument(sensorId, documentId, 'body', false, {
        context: skipGlobalErrorToast(),
      }),
    );
  }
}
