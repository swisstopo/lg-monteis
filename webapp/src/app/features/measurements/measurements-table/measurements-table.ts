import { DatePipe } from '@angular/common';
import {
  Component,
  computed,
  effect,
  inject,
  inputBinding,
  outputBinding,
  signal,
} from '@angular/core';
import { form, FormField, required, schema } from '@angular/forms/signals';
import { MatButton } from '@angular/material/button';
import {
  MatDatepicker,
  MatDatepickerInput,
  MatDatepickerToggle,
} from '@angular/material/datepicker';
import { MatDialog } from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { MatError, MatFormField, MatInput, MatLabel, MatSuffix } from '@angular/material/input';
import {
  MatTimepicker,
  MatTimepickerInput,
  MatTimepickerToggle,
} from '@angular/material/timepicker';
import { APP_ISO_TIMESTAMP_FORMAT } from '@core/date/date.provider';
import { MeasurementResponseDto } from '@core/generated';
import { FormErrorService } from '@core/utils/form-error.service';
import { MeasurementsService } from '@features/measurements/services/measurements.service';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { WorkbenchView } from '@scion/workbench';
import { ChartComponent, ChartOptions, ChartRangeEvent, createTimeChartOptions } from '@ui/chart';
import { InlineError } from '@ui/inline-error/inline-error';
import { createPagedDatasource } from '@ui/table/paged-datasource.factory';
import Table from '@ui/table/table';
import { GridApi } from 'ag-grid-community';
import { subDays } from 'date-fns';
import { createColumns } from './columns';

interface DateTimeModel {
  date: Date | null;
  /** Time-of-day carrier; only its hours/minutes are used (MatTimepicker works with `Date`). */
  time: Date | null;
}

interface DateRangeModel {
  start: DateTimeModel;
  end: DateTimeModel;
}

/** Builds a `Date` at a fixed time of day, used for the default start/end times. */
function atTime(hours: number, minutes: number): Date {
  const date = new Date();
  date.setHours(hours, minutes, 0, 0);
  return date;
}

/** Combines a date-only `Date` with the hours/minutes of a time-only `Date`. */
function combineDateAndTime(date: Date | null, time: Date | null): Date | null {
  if (!date) return null;
  const combined = new Date(date);
  combined.setHours(time?.getHours() ?? 0, time?.getMinutes() ?? 0, 0, 0);
  return combined;
}

@Component({
  selector: 'app-measurements-table',
  imports: [
    Table,
    TranslatePipe,
    MatButton,
    MatIcon,
    MatLabel,
    MatFormField,
    MatSuffix,
    MatInput,
    MatDatepickerToggle,
    MatDatepicker,
    MatDatepickerInput,
    MatTimepicker,
    MatTimepickerInput,
    MatTimepickerToggle,
    FormField,
    MatError,
    InlineError,
  ],
  providers: [DatePipe],
  templateUrl: './measurements-table.html',
  styleUrl: './measurements-table.scss',
})
export default class MeasurementsTable {
  private readonly datePipe = inject(DatePipe);
  private readonly translateService = inject(TranslateService);
  private readonly dialog = inject(MatDialog);
  protected readonly measurementsService = inject(MeasurementsService);
  private readonly formErrorService = inject(FormErrorService);
  readonly serviceError = this.measurementsService.error;
  private readonly currentRange = signal<{ start: Date; end: Date } | null>(null);
  readonly dateRangeModel = signal<DateRangeModel>({
    start: { date: subDays(new Date(), 10), time: atTime(0, 0) },
    end: { date: new Date(), time: new Date() },
  });

  readonly rangeForm = form(
    this.dateRangeModel,
    schema((path) => {
      required(path.start.date, { message: 'Start date is required' });
      required(path.end.date, { message: 'End date is required' });
      required(path.start.time, { message: 'Start time is required' });
      required(path.end.time, { message: 'End time is required' });
    }),
  );

  protected totalCount = signal<number | undefined>(undefined);
  protected loadError = signal(false);
  private readonly gridApi = signal<GridApi | undefined>(undefined);

  protected datasource = createPagedDatasource(
    (params) => this.measurementsService.getMeasurements(params),
    this.totalCount,
    this.loadError,
  );

  protected readonly selectedRows = signal<MeasurementResponseDto[]>([]);

  protected readonly selectedSensorIds = computed<string[]>(() => {
    const ids = this.selectedRows()
      .map((metric) => metric.sensorParameterId)
      .filter((e) => e != null);
    return this.distinct(ids);
  });

  protected wrappedCols = createColumns(this.datePipe);

  private readonly plottedIds = signal<string[]>([]);

  constructor(view: WorkbenchView) {
    // SCION Workbench: Dynamically update the tab title whenever the data changes
    effect(() => {
      const count = this.totalCount() ?? 0;
      view.title = this.translateService.translate('tab.measurements-table', { count })();
    });

    effect(() => {
      const errors = this.measurementsService.error();
      if (errors) {
        this.formErrorService.mapApiErrorsToFormErrors(
          errors,
          this.rangeForm,
          'chart.error.unspecified.message',
        );
        this.dialog.closeAll();
      }
    });
  }

  onGridReady(api: GridApi): void {
    this.gridApi.set(api);
    api.setGridOption('datasource', this.datasource);
  }

  resetRange(): void {
    this.dateRangeModel.set({
      start: { date: null, time: atTime(0, 0) },
      end: { date: null, time: atTime(23, 59) },
    });
  }

  onWrappedRow(row: MeasurementResponseDto) {
    console.log(row);
  }

  onSelectionChanged(rows: MeasurementResponseDto[]): void {
    this.selectedRows.set(rows);
  }

  protected getMeasurementRowId = (row: MeasurementResponseDto): string =>
    row.sensorParameterId ?? '';

  protected onPlot() {
    if (this.rangeForm().invalid()) {
      this.rangeForm().markAsTouched();
      return;
    }
    if (this.selectedSensorIds().length === 0) return;

    const { start, end } = this.dateRangeModel();
    const rangeStart = combineDateAndTime(start.date, start.time);
    const rangeEnd = combineDateAndTime(end.date, end.time);
    if (!rangeStart || !rangeEnd) return;

    this.plottedIds.set(this.selectedSensorIds());

    this.fetchChartData(rangeStart, rangeEnd);

    const title = computed((): string => {
      const range = this.currentRange();
      if (!range) return '';

      return this.translateService.translate('chart.title', {
        name: 'MyFancyExperiment',
        rangeFrom: this.datePipe.transform(range.start),
        rangeTo: this.datePipe.transform(range.end),
      })();
    });

    const chartOptions = computed((): ChartOptions => {
      const data = this.measurementsService.chartData.value();
      return createTimeChartOptions({
        title: title(),
        xAxisLabel: 'Date',
        yAxisLabels: data?.yAxisLabels ?? {},
        subtitle: this.translateService.translate('chart.subtitle', {
          count: data?.count ?? '',
        })(),
      });
    });

    this.dialog.open(ChartComponent, {
      width: '95vw',
      maxWidth: '100%',
      height: '95vh',
      maxHeight: '100%',
      autoFocus: true,
      bindings: [
        inputBinding('title', () => title()),
        inputBinding('datasets', () => this.measurementsService.chartData.value()?.datasets ?? []),
        inputBinding('options', chartOptions),
        outputBinding('rangeSelected', (range) => this.onRangeSelected(<ChartRangeEvent>range)),
      ],
    });
  }

  private onRangeSelected(range: ChartRangeEvent): void {
    this.fetchChartData(new Date(range.min), new Date(range.max));
  }

  private fetchChartData(start: Date, end: Date): void {
    this.currentRange.set({ start, end });
    const rangeFromTimestamp = this.datePipe.transform(start, APP_ISO_TIMESTAMP_FORMAT)!;
    const rangeToTimestamp = this.datePipe.transform(end, APP_ISO_TIMESTAMP_FORMAT)!;

    this.measurementsService.getChartData(this.plottedIds(), rangeFromTimestamp, rangeToTimestamp);
  }

  /** Preserves first-occurrence order. */
  private distinct<T>(values: readonly T[]): T[] {
    return [...new Set(values)];
  }
}
