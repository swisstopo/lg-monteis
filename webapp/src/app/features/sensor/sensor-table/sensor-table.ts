import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { PermissionsService } from '@core/auth/permissions.service';
import {
  FulcrumRefreshResponseDto,
  SensorParameterRowResponseDto,
  SkippedSensorDto,
} from '@core/generated';
import { ToastService } from '@core/notifications/toast.service';
import SensorEdit from '@features/sensor/sensor-edit/sensor-edit';
import { SensorService } from '@features/sensor/services/sensor.service';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { WorkbenchView } from '@scion/workbench';
import { FormDialogService } from '@ui/dialog/form-dialog.service';
import { FileDownloadService } from '@ui/file-download/file-download.service';
import { InlineError } from '@ui/inline-error/inline-error';
import { TableHeader } from '@ui/table-header/table-header';
import { createPagedDatasource } from '@ui/table/paged-datasource.factory';
import { toGridFilterSortParams } from '@ui/table/paged-request.mapper';
import Table from '@ui/table/table';
import { GridApi } from 'ag-grid-community';
import { createColumns } from './columns';

const MAX_NAMED_SKIPPED_SENSORS = 10;

@Component({
  selector: 'app-sensor-table',
  imports: [Table, TableHeader, TranslatePipe, MatIcon, MatButton, MatProgressSpinner, InlineError],
  templateUrl: './sensor-table.html',
  styleUrl: './sensor-table.scss',
  providers: [DatePipe],
})
export default class SensorTable {
  private readonly formDialog = inject(FormDialogService);
  protected sensorService = inject(SensorService);
  private readonly translateService = inject(TranslateService);
  private readonly fileDownloadService = inject(FileDownloadService);
  private readonly toastService = inject(ToastService);
  protected readonly permissions = inject(PermissionsService);

  readonly searchTerm = signal<string>('');

  protected wrappedCols = createColumns();
  // one row per parameter, a sensor counts once however many of its rows are selected
  protected selectedSensors = signal<SensorParameterRowResponseDto[]>([]);
  protected selectedSensorId = computed(() => {
    const sensors = this.selectedSensors();
    return sensors.length === 1 ? sensors[0].sensorId : undefined;
  });
  protected totalCount = signal<number | undefined>(undefined);
  protected loadError = signal(false);
  protected downloading = signal(false);
  protected refreshing = signal(false);
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

    // the infinite row model has no way to notice a save in the dialog, so the visible pages are
    // fetched again on every one
    effect(() => {
      this.sensorService.sensorsSaved();
      untracked(() => this.gridApi()?.refreshInfiniteCache());
    });
  }

  onGridReady(api: GridApi): void {
    this.gridApi.set(api);
  }

  onSelectionChanged(rows: SensorParameterRowResponseDto[]): void {
    const bySensorId = new Map(rows.map((row) => [row.sensorId, row]));
    this.selectedSensors.set([...bySensorId.values()]);
  }

  onCreate(): void {
    this.formDialog.open(SensorEdit);
  }

  onView(): void {
    this.openSelected(true);
  }

  onEdit(): void {
    this.openSelected(false);
  }

  private openSelected(viewOnly: boolean): void {
    const sensorId = this.selectedSensorId();
    if (sensorId !== undefined) {
      this.formDialog.open(SensorEdit, { sensorId, viewOnly });
    }
  }

  async onDownload(): Promise<void> {
    const api = this.gridApi();
    if (!api) return;

    this.downloading.set(true);
    try {
      const { sortModel, filterModel } = toGridFilterSortParams(api);
      const blob = await this.sensorService.getSensorsCsv(sortModel, filterModel);
      this.fileDownloadService.download(blob, 'sensors.csv');
    } catch {
      this.toastService.error(this.translateService.translate('sensor.error.downloadFailed')());
    } finally {
      this.downloading.set(false);
    }
  }

  async onRefresh(): Promise<void> {
    const sensors = this.selectedSensors();
    this.refreshing.set(true);
    try {
      const result = await this.sensorService.refreshFromFulcrum(
        sensors.map((sensor) => sensor.sensorId!),
      );
      this.toastRefreshResult(result);
    } catch {
      // the global error toast already reports it
    } finally {
      this.refreshing.set(false);
    }
  }

  private toastRefreshResult(result: FulcrumRefreshResponseDto): void {
    const counts = { checked: result.checked, updated: result.updated };
    const skipped = result.skipped ?? [];
    if (skipped.length === 0) {
      this.toastService.success(
        this.translateService.translate('sensor.fulcrumRefresh.success', counts)(),
      );
      return;
    }
    this.toastService.warning(
      this.translateService.translate('sensor.fulcrumRefresh.partial', {
        ...counts,
        skippedSensors: this.describeSkipped(skipped),
      })(),
    );
  }

  private describeSkipped(skipped: SkippedSensorDto[]): string {
    // no name for NOT_FOUND: sensor unknown or hidden from caller
    const listed = skipped
      .slice(0, MAX_NAMED_SKIPPED_SENSORS)
      .map((sensor) => `${sensor.sensorName ?? sensor.sensorId} (${this.skipReasonOf(sensor)})`);
    const notListedCount = skipped.length - listed.length;
    if (notListedCount === 0) {
      return listed.join(', ');
    }
    return this.translateService.translate('sensor.fulcrumRefresh.andMore', {
      sensors: listed.join(', '),
      count: notListedCount,
    })();
  }

  private skipReasonOf(sensor: SkippedSensorDto): string {
    return this.translateService.translate(`sensor.fulcrumRefresh.reason.${sensor.reason}`)();
  }

  onSearch(term: string): void {
    this.searchTerm.set(term);
  }

  // One row per (Sensor, SensorParameter) pair; a sensor with no parameters still gets exactly
  // one row (parameter is null), so its own id has to stand in for the missing parameter id.
  protected getSensorParameterRowId = (row: SensorParameterRowResponseDto): string =>
    `${row.sensorId}::${row.parameter?.id ?? 'none'}`;
}
