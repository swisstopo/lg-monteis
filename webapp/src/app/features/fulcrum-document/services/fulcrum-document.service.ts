import { Injectable, inject } from '@angular/core';
import {
  FulcrumDocumentControllerService,
  FulcrumEntryResponseDto,
  FulcrumSyncRunResponseDto,
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

  // 204 when no sync ever ran, the client hands that on as a null body
  async getLatestSyncRun(): Promise<FulcrumSyncRunResponseDto | undefined> {
    const run = await firstValueFrom(
      this.api.getLatestFulcrumSyncRun('body', false, { context: skipGlobalErrorToast() }),
    );
    return run ?? undefined;
  }

  startSync(): Promise<FulcrumSyncRunResponseDto> {
    return firstValueFrom(
      this.api.startFulcrumSync('body', false, { context: skipGlobalErrorToast() }),
    );
  }
}
