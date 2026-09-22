import { Component, effect, inject, inputBinding, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { SensorParameterRowResponseDto } from '@core/generated';
import { toErrorDtos } from '@core/http/api-error.model';
import { ToastService } from '@core/notifications/toast.service';
import SensorEdit from '@features/sensor/sensor-edit/sensor-edit';
import { SensorService } from '@features/sensor/services/sensor.service';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { WorkbenchView } from '@scion/workbench';
import { InlineError } from '@ui/inline-error/inline-error';
import { TableHeader } from '@ui/table-header/table-header';
import { CsvDownloadService } from '@ui/table/csv-download.service';
import { createPagedDatasource } from '@ui/table/paged-datasource.factory';
import { toGridFilterSortParams } from '@ui/table/paged-request.mapper';
import Table from '@ui/table/table';
import { GridApi } from 'ag-grid-community';
import { createColumns } from './columns';

@Component({
  selector: 'app-sensor-table',
  imports: [Table, TableHeader, TranslatePipe, MatIcon, MatButton, MatProgressSpinner, InlineError],
  templateUrl: './sensor-table.html',
  styleUrl: './sensor-table.scss',
})
export default class SensorTable {
  private readonly dialog = inject(MatDialog);
  protected sensorService = inject(SensorService);
  private readonly translateService = inject(TranslateService);
  private readonly csvDownloadService = inject(CsvDownloadService);
  private readonly toastService = inject(ToastService);

  readonly searchTerm = signal<string>('');

  protected wrappedCols = createColumns();
  protected selectedSensorId = signal<string | undefined>(undefined);
  protected totalCount = signal<number | undefined>(undefined);
  protected loadError = signal(false);
  protected downloading = signal(false);
  private readonly gridApi = signal<GridApi | undefined>(undefined);

  protected datasource = createPagedDatasource(
    (params) => this.sensorService.getSensors(params),
    this.totalCount,
    this.loadError,
  );

  constructor(view: WorkbenchView) {
    // SCION Workbench: Dynamically update the tab title whenever the data changes
    effect(() => {
      view.title = this.translateService.translate('tab.sensor')();
    });

    // Re-fetch the currently visible pages whenever a sensor is created/updated elsewhere
    // (e.g. via the edit dialog) - the infinite row model otherwise has no way to know.
    effect(() => {
      if (this.sensorService.sensorsChanged()) {
        this.gridApi()?.refreshInfiniteCache();
        this.sensorService.sensorsChanged.set(false);
      }
    });
  }

  onGridReady(api: GridApi): void {
    this.gridApi.set(api);
  }

  onWrappedRow(row: SensorParameterRowResponseDto) {
    console.log(row);
  }

  onSelectionChanged(rows: SensorParameterRowResponseDto[]): void {
    this.selectedSensorId.set(rows[0]?.sensorId);
  }

  onCreate(): void {
    this.dialog.open(SensorEdit, { width: '60vw', maxWidth: '1200px', autoFocus: true });
  }

  onEdit(): void {
    const sensorId = this.selectedSensorId();
    if (sensorId === undefined) return;

    this.dialog.open(SensorEdit, {
      width: '60vw',
      maxWidth: '1200px',
      autoFocus: true,
      bindings: [inputBinding('sensorId', () => sensorId)],
    });
  }

  async onDelete(): Promise<void> {
    const id = this.selectedSensorId();
    if (!id) return;

    if (!confirm(this.translateService.translate('sensor.delete.confirm')())) {
      return;
    }

    try {
      await this.sensorService.deleteSensor(id);
      this.toastService.success(this.translateService.translate('sensor.delete.success')());
      this.selectedSensorId.set(undefined);
    } catch (err) {
      const errors = toErrorDtos(err);
      errors.forEach((e) =>
        this.toastService.error(
          this.translateService.translate(e?.messageKey ?? 'sensor.error.unspecified.message')(),
          this.translateService.translate('sensor.error.unspecified.title')(),
        ),
      );
    }
  }

  async onDownload(): Promise<void> {
    const api = this.gridApi();
    if (!api) return;

    this.downloading.set(true);
    try {
      const { sortModel, filterModel } = toGridFilterSortParams(api);
      const blob = await this.sensorService.getSensorsCsv(sortModel, filterModel);
      this.csvDownloadService.download(blob, 'sensors.csv');
    } catch {
      this.toastService.error(this.translateService.translate('sensor.error.downloadFailed')());
    } finally {
      this.downloading.set(false);
    }
  }

  onSearch(term: string): void {
    this.searchTerm.set(term);
  }

  // One row per (Sensor, SensorParameter) pair; a sensor with no parameters still gets exactly
  // one row (parameter is null), so its own id has to stand in for the missing parameter id.
  protected getSensorParameterRowId = (row: SensorParameterRowResponseDto): string =>
    `${row.sensorId}::${row.parameter?.id ?? 'none'}`;
}
