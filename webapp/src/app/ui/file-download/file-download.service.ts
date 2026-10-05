import { DOCUMENT, inject, Injectable } from '@angular/core';

// the new tab loads the object URL asynchronously, revoking it right away leaves the tab blank
const NEW_TAB_URL_LIFETIME_MS = 60_000;

/** Hands an already fetched blob to the browser, as a download or in a new tab. */
@Injectable({ providedIn: 'root' })
export class FileDownloadService {
  private readonly document = inject(DOCUMENT);

  download(blob: Blob, fileName: string): void {
    const objectUrl = URL.createObjectURL(blob);
    try {
      const anchor = this.document.createElement('a');
      anchor.href = objectUrl;
      anchor.download = fileName;
      anchor.click();
    } finally {
      URL.revokeObjectURL(objectUrl);
    }
  }

  /**
   * Opens the blob `load` resolves to in a new tab. The tab opens before `load` is awaited,
   * browsers block a popup that is no longer tied to the click.
   */
  async openInNewTab(load: () => Promise<Blob>): Promise<void> {
    const tab = this.document.defaultView?.open('', '_blank') ?? null;
    try {
      const objectUrl = URL.createObjectURL(await load());
      if (tab) {
        tab.location.href = objectUrl;
      }
      setTimeout(() => URL.revokeObjectURL(objectUrl), NEW_TAB_URL_LIFETIME_MS);
    } catch (error) {
      tab?.close();
      throw error;
    }
  }
}
