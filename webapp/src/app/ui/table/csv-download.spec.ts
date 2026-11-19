import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ToastService } from '@core/notifications/toast.service';
import { provideTranslateService, TranslateService } from '@ngx-translate/core';
import { FileDownloadService } from '@ui/file-download/file-download.service';
import { GridApi } from 'ag-grid-community';
import { beforeEach, describe, expect, it, Mock, vi } from 'vitest';
import { createCsvDownload, CsvDownload, CsvDownloadOptions } from './csv-download';

const gridApi = {
  getFilterModel: () => ({}),
  getColumnState: () => [],
} as unknown as GridApi;

describe('createCsvDownload', () => {
  let fileDownload: { download: ReturnType<typeof vi.fn> };
  let toast: { warning: ReturnType<typeof vi.fn>; error: ReturnType<typeof vi.fn> };
  let fetchCsv: Mock<CsvDownloadOptions['fetchCsv']>;

  beforeEach(() => {
    fileDownload = { download: vi.fn() };
    toast = { warning: vi.fn(), error: vi.fn() };
    fetchCsv = vi.fn<CsvDownloadOptions['fetchCsv']>().mockResolvedValue(new Blob(['name']));
    TestBed.configureTestingModule({
      providers: [
        { provide: FileDownloadService, useValue: fileDownload },
        { provide: ToastService, useValue: toast },
        provideTranslateService(),
      ],
    });
  });

  function create(api: GridApi | undefined = gridApi): CsvDownload {
    return TestBed.runInInjectionContext(() =>
      createCsvDownload({
        gridApi: signal(api),
        fetchCsv,
        fileName: 'things.csv',
        failedMessageKey: 'thing.error.downloadFailed',
      }),
    );
  }

  it('downloads the CSV under the file name', async () => {
    const csvDownload = create();

    await csvDownload.download();

    expect(fetchCsv).toHaveBeenCalledWith(undefined, undefined);
    expect(fileDownload.download.mock.calls[0][1]).toBe('things.csv');
    expect(csvDownload.downloading()).toBe(false);
    expect(toast.error).not.toHaveBeenCalled();
  });

  it('does nothing before the grid is ready', async () => {
    await create(undefined).download();

    expect(fetchCsv).not.toHaveBeenCalled();
  });

  it('warns with the message the server refuses a too large table with', async () => {
    const translate = vi.spyOn(TestBed.inject(TranslateService), 'translate');
    fetchCsv.mockRejectedValue(
      new HttpErrorResponse({
        status: 400,
        error: new Blob([
          '{"target":"GLOBAL","messageKey":"export.tooManyRows","params":{"max":1000}}',
        ]),
      }),
    );

    await create().download();

    expect(translate).toHaveBeenCalledWith('export.tooManyRows', { max: 1000 });
    expect(toast.warning).toHaveBeenCalled();
    expect(toast.error).not.toHaveBeenCalled();
    expect(fileDownload.download).not.toHaveBeenCalled();
  });

  it('toasts the failed message when the download fails otherwise', async () => {
    const translate = vi.spyOn(TestBed.inject(TranslateService), 'translate');
    fetchCsv.mockRejectedValue(new Error('boom'));

    await create().download();

    expect(translate).toHaveBeenCalledWith('thing.error.downloadFailed');
    expect(toast.error).toHaveBeenCalled();
    expect(fileDownload.download).not.toHaveBeenCalled();
  });
});
