import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MeasurementControllerService, MeasurementResponseDto } from '@core/generated';
import { ToastService } from '@core/notifications/toast.service';
import { provideTranslateService, TranslateService } from '@ngx-translate/core';
import { WorkbenchView } from '@scion/workbench';
import { FileDownloadService } from '@ui/file-download/file-download.service';
import { GridApi } from 'ag-grid-community';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import MeasurementsTable from './measurements-table';

const measurementControllerMock = {
  getMeasurements: vi.fn().mockReturnValue(of({ rows: [], totalCount: 0 })),
  getChartData: vi.fn().mockReturnValue(of([])),
  getMeasurementsCsv: vi.fn(),
};

interface DownloadAccess {
  gridApi: { set: (api: GridApi) => void };
  onDownload: () => Promise<void>;
}

describe('MeasurementsTable', () => {
  let fixture: ComponentFixture<MeasurementsTable>;
  let fileDownload: { download: ReturnType<typeof vi.fn> };
  let toast: { warning: ReturnType<typeof vi.fn>; error: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    measurementControllerMock.getMeasurements.mockReturnValue(of({ rows: [], totalCount: 0 }));
    measurementControllerMock.getMeasurementsCsv.mockReset();
    measurementControllerMock.getMeasurementsCsv.mockReturnValue(of(new Blob(['experimentName'])));
    fileDownload = { download: vi.fn() };
    toast = { warning: vi.fn(), error: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [MeasurementsTable],
      providers: [
        {
          provide: MeasurementControllerService,
          useValue: measurementControllerMock,
        },
        { provide: FileDownloadService, useValue: fileDownload },
        { provide: ToastService, useValue: toast },
        WorkbenchView,
        provideTranslateService(),
        provideNativeDateAdapter(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(MeasurementsTable);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('derives distinct, defined sensor ids from the selected rows only', () => {
    const component = TestBed.createComponent(MeasurementsTable).componentInstance as unknown as {
      selectedRows: { set: (rows: MeasurementResponseDto[]) => void };
      selectedSensorIds: () => string[];
    };

    component.selectedRows.set([
      { sensorParameterId: '1' },
      { sensorParameterId: '2' },
      { sensorParameterId: '2' },
      { sensorParameterId: undefined },
    ]);

    expect(component.selectedSensorIds()).toEqual(['1', '2']);
  });

  function downloadable(): DownloadAccess {
    const component = fixture.componentInstance as unknown as DownloadAccess;
    component.gridApi.set({
      getSortModel: () => [],
      getFilterModel: () => ({}),
      getColumnState: () => [],
    } as unknown as GridApi);
    return component;
  }

  it('downloads the filtered measurements as CSV', async () => {
    await downloadable().onDownload();

    expect(measurementControllerMock.getMeasurementsCsv).toHaveBeenCalled();
    expect(fileDownload.download.mock.calls[0][1]).toBe('measurements.csv');
    expect(toast.error).not.toHaveBeenCalled();
  });

  it('shows the message the server refuses a too large table with as a warning', async () => {
    const translate = vi.spyOn(TestBed.inject(TranslateService), 'translate');
    measurementControllerMock.getMeasurementsCsv.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: new Blob([
              '{"target":"GLOBAL","messageKey":"export.tooManyRows","params":{"max":1000}}',
            ]),
          }),
      ),
    );

    await downloadable().onDownload();

    expect(translate).toHaveBeenCalledWith('measurements-table.downloadTooManyRows', {
      max: 1000,
    });
    expect(toast.warning).toHaveBeenCalled();
    expect(toast.error).not.toHaveBeenCalled();
    expect(fileDownload.download).not.toHaveBeenCalled();
  });

  it('toasts an error when the download fails', async () => {
    measurementControllerMock.getMeasurementsCsv.mockReturnValue(
      throwError(() => new Error('boom')),
    );

    await downloadable().onDownload();

    expect(toast.error).toHaveBeenCalled();
    expect(fileDownload.download).not.toHaveBeenCalled();
  });
});
