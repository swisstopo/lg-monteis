import { Injectable } from '@angular/core';

/**
 * Triggers a browser download of an already-fetched CSV blob (e.g. the body of a
 * GET /api/sensors/csv response) via a temporary anchor element.
 */
@Injectable({ providedIn: 'root' })
export class CsvDownloadService {
  download(blob: Blob, filename: string): void {
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
