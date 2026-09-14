import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { skipGlobalErrorToast } from '@core/http/http-context';
import { firstValueFrom } from 'rxjs';

export interface CsvDownloadParams {
  sortModel?: string;
  filterModel?: string;
}

/**
 * Triggers a CSV file download from a backend export endpoint (e.g. GET /api/sensors/csv).
 *
 * Goes through HttpClient (like MeasurementsTiles does for binary tile data) rather than a raw
 * fetch(), so the existing interceptors - authorization above all - apply the same way they do to
 * every other request. Opts out of the global error toast via skipGlobalErrorToast(): callers
 * render their own download-specific error instead.
 */
@Injectable({ providedIn: 'root' })
export class CsvDownloadService {
  private readonly httpClient = inject(HttpClient);

  async download(path: string, filename: string, params: CsvDownloadParams = {}): Promise<void> {
    const query: Record<string, string> = {};
    if (params.sortModel) query['sortModel'] = params.sortModel;
    if (params.filterModel) query['filterModel'] = params.filterModel;

    const blob = await firstValueFrom(
      this.httpClient.get(path, {
        params: query,
        responseType: 'blob',
        context: skipGlobalErrorToast(),
      }),
    );

    const objectUrl = URL.createObjectURL(blob);
    try {
      const anchor = document.createElement('a');
      anchor.href = objectUrl;
      anchor.download = filename;
      anchor.click();
    } finally {
      URL.revokeObjectURL(objectUrl);
    }
  }
}
