import { inject, Signal, signal } from '@angular/core';
import { toErrorDtosFromBlob } from '@core/http/api-error.model';
import { ToastService } from '@core/notifications/toast.service';
import { TranslateService } from '@ngx-translate/core';
import { FileDownloadService } from '@ui/file-download/file-download.service';
import { GridApi } from 'ag-grid-community';
import { toGridFilterSortParams } from './paged-request.mapper';

// the backend refuses an export matching more rows than it allows (TooManyExportRowsException)
const TOO_MANY_ROWS_MESSAGE_KEY = 'export.tooManyRows';

export interface CsvDownloadOptions {
  gridApi: Signal<GridApi | undefined>;
  fetchCsv: (sortModel?: string, filterModel?: string) => Promise<Blob>;
  fileName: string;
  /** Toasted when the download fails for any other reason than too many rows. */
  failedMessageKey: string;
}

export interface CsvDownload {
  readonly downloading: Signal<boolean>;
  download(): Promise<void>;
}

/**
 * Downloads a feature table's rows as CSV, filtered and sorted the way its grid currently is.
 * Shared by every feature table with a download button (sensor, experiment, ...). A refused
 * export (too many rows) is toasted as a warning with the backend's message, any other failure as
 * an error with {@link CsvDownloadOptions.failedMessageKey}.
 *
 * Injects its services, so it has to be called in an injection context (a field initializer).
 */
export function createCsvDownload(options: CsvDownloadOptions): CsvDownload {
  const fileDownloadService = inject(FileDownloadService);
  const toastService = inject(ToastService);
  const translateService = inject(TranslateService);
  const downloading = signal(false);

  async function download(): Promise<void> {
    const api = options.gridApi();
    if (!api) return;

    downloading.set(true);
    try {
      const { sortModel, filterModel } = toGridFilterSortParams(api);
      const blob = await options.fetchCsv(sortModel, filterModel);
      fileDownloadService.download(blob, options.fileName);
    } catch (error) {
      const tooManyRows = (await toErrorDtosFromBlob(error)).find(
        (dto) => dto.messageKey === TOO_MANY_ROWS_MESSAGE_KEY,
      );
      if (tooManyRows) {
        toastService.warning(
          translateService.translate(TOO_MANY_ROWS_MESSAGE_KEY, tooManyRows.params)(),
        );
      } else {
        toastService.error(translateService.translate(options.failedMessageKey)());
      }
    } finally {
      downloading.set(false);
    }
  }

  return { downloading: downloading.asReadonly(), download };
}
