import { Injectable, inject } from '@angular/core';
import {
  FulcrumDocumentControllerService,
  FulcrumDocumentResponseDto,
  FulcrumSyncStatusResponseDto,
} from '@core/generated';
import { skipGlobalErrorToast } from '@core/http/http-context';
import { firstValueFrom } from 'rxjs';

/**
 * Content and sync requests skip the global error toast: the table toasts a failed download itself,
 * a sync that is already running is no error, and a toast per failed status poll would bury the UI.
 */
@Injectable({ providedIn: 'root' })
export class FulcrumDocumentService {
  private readonly api = inject(FulcrumDocumentControllerService);

  getDocuments(): Promise<FulcrumDocumentResponseDto[]> {
    return firstValueFrom(this.api.getFulcrumDocuments());
  }

  getContent(documentId: string): Promise<Blob> {
    return firstValueFrom(
      this.api.downloadFulcrumDocumentFile(documentId, 'body', false, {
        context: skipGlobalErrorToast(),
      }),
    );
  }

  getSyncStatus(): Promise<FulcrumSyncStatusResponseDto> {
    return firstValueFrom(
      this.api.getFulcrumSyncStatus('body', false, { context: skipGlobalErrorToast() }),
    );
  }

  startSync(): Promise<FulcrumSyncStatusResponseDto> {
    return firstValueFrom(
      this.api.startFulcrumSync('body', false, { context: skipGlobalErrorToast() }),
    );
  }
}
