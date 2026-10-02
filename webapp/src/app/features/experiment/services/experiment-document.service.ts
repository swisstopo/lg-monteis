import { Injectable, inject } from '@angular/core';
import {
  ExperimentDocumentControllerService,
  ExperimentDocumentResponseDto,
} from '@core/generated';
import { firstValueFrom } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ExperimentDocumentService {
  private readonly api = inject(ExperimentDocumentControllerService);

  getDocuments(experimentId: string): Promise<ExperimentDocumentResponseDto[]> {
    return firstValueFrom(this.api.getDocuments(experimentId));
  }

  uploadDocument(experimentId: string, file: File): Promise<ExperimentDocumentResponseDto> {
    return firstValueFrom(this.api.uploadDocument(experimentId, file));
  }

  getContent(experimentId: string, documentId: string): Promise<Blob> {
    return firstValueFrom(this.api.downloadDocument(experimentId, documentId));
  }
}
