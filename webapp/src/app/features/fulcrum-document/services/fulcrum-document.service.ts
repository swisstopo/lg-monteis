import { Injectable, inject } from '@angular/core';
import {
  FulcrumDocumentControllerService,
  FulcrumEntryResponseDto,
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

  getEntries(): Promise<FulcrumEntryResponseDto[]> {
    return firstValueFrom(this.api.getFulcrumEntries());
  }

  getContent(documentId: string): Promise<Blob> {
    return firstValueFrom(
      this.api.downloadFulcrumDocument(documentId, 'body', false, {
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
