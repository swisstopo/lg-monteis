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
import { rxResource } from '@angular/core/rxjs-interop';
import { form, FormField, required, schema } from '@angular/forms/signals';
import { MatButton } from '@angular/material/button';
import {
  MatDatepickerToggle,
  MatDateRangeInput,
  MatDateRangePicker,
  MatEndDate,
  MatStartDate,
} from '@angular/material/datepicker';
import { MatDialog } from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { MatError, MatFormField, MatLabel, MatSuffix } from '@angular/material/input';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { WorkbenchView } from '@scion/workbench';
import { APP_ISO_TIMESTAMP_FORMAT } from '../../../core/date/date.provider';
import { OverviewControllerService, ReadSimpleMetricDto } from '../../../core/generated';
import { FormErrorService } from '../../../core/utils/form-error.service';
import {
  ChartComponent,
  ChartOptions,
  ChartRangeEvent,
  createTimeChartOptions,
} from '../../../ui/chart';
import Table from '../../../ui/table/table';
import { MesurementsService } from '../services/mesurements.service';
import { createColumns } from './columns';

interface DateRangeModel {
  start: Date | null;
  end: Date | null;
}

@Component({
  selector: 'app-measurements-overview',
  imports: [
    Table,
    TranslatePipe,
    MatButton,
    MatIcon,
    MatLabel,
    MatFormField,
    MatSuffix,
    MatDatepickerToggle,
    MatDateRangeInput,
    MatDateRangePicker,
    MatStartDate,
    MatEndDate,
    FormField,
    MatError,
  ],
  providers: [DatePipe],
  templateUrl: './overview-table.html',
  styleUrl: './overview-table.scss',
})
export default class OverviewTable {
  private readonly datePipe = inject(DatePipe);
  private readonly i18nService = inject(TranslateService);
  private readonly dialog = inject(MatDialog);
  protected readonly mesurementsService = inject(MesurementsService);
  protected readonly overviewService = inject(OverviewControllerService);
  private readonly formErrorService = inject(FormErrorService);
  readonly serviceError = this.mesurementsService.error;
  readonly dateRangeModel = signal<DateRangeModel>({
    start: null,
    end: null,
  });

  readonly rangeForm = form(
    this.dateRangeModel,
    schema((path) => {
      required(path.start, { message: 'Start date is required' });
      required(path.end, { message: 'End date is required' });
    }),
  );

  protected metricsResource = rxResource({
    stream: () => this.overviewService.getMetrics(50),
  });

  protected wrappedCols = createColumns(this.datePipe);

  /** Ids of the sensors currently plotted, kept so a zoom selection can refetch a narrower range. */
  private readonly plottedIds = signal<number[]>([]);

  constructor(view: WorkbenchView) {
    // SCION Workbench: Dynamically update the tab title whenever the data changes
    effect(() => {
      view.title = this.i18nService.instant('tab.overview');
    });

    effect(() => {
      const errors = this.mesurementsService.error();
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

  resetRange(): void {
    this.dateRangeModel.set({ start: null, end: null });
  }

  onWrappedRow(row: ReadSimpleMetricDto) {
    console.log(row);
  }

  protected getMetricRowId = (row: ReadSimpleMetricDto): string =>
    `${row.sensorId}-${row.timestamp}`;

  protected onPlot() {
    if (this.rangeForm().invalid()) {
      this.rangeForm().markAsTouched();
      return;
    }
    const { start, end } = this.dateRangeModel();
    if (!start || !end) return;

    //L118-L125 should be replaced by proper table implementation logic
    const rangeStart = new Date(start);
    rangeStart.setHours(0, 0, 0, 0);
    const rangeEnd = new Date(end);
    rangeEnd.setHours(23, 59, 59, 999);
    this.plottedIds.set([1, 2, 3, 5]);
    //

    this.fetchChartData(rangeStart, rangeEnd);

    const title = this.i18nService.translate('chart.title', {
      name: 'MyFancyExperiment', // should be replaced by experiment name
      rangeFrom: this.datePipe.transform(start),
      rangeTo: this.datePipe.transform(end),
    })();

    // use computed to avoid re-creating the chart options on every change
    // angular's inputBinding normally re-evaluates on every change
    const chartOptions = computed((): ChartOptions => {
      const data = this.mesurementsService.chartData.value();
      return createTimeChartOptions({
        title,
        xAxisLabel: 'Date',
        yAxisLabels: data?.yAxisLabels ?? {},
        subtitle: this.i18nService.translate('chart.subtitle', {
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
        inputBinding('title', () => title),
        inputBinding('datasets', () => this.mesurementsService.chartData.value()?.datasets ?? []),
        inputBinding('options', chartOptions),
        outputBinding('rangeSelected', (range) => this.onRangeSelected(<ChartRangeEvent>range)),
      ],
    });
  }

  private onRangeSelected(range: ChartRangeEvent): void {
    this.fetchChartData(new Date(range.min), new Date(range.max));
  }

  private fetchChartData(start: Date, end: Date): void {
    const rangeFromTimestamp = this.datePipe.transform(start, APP_ISO_TIMESTAMP_FORMAT)!;
    const rangeToTimestamp = this.datePipe.transform(end, APP_ISO_TIMESTAMP_FORMAT)!;

    this.mesurementsService.getChartData(this.plottedIds(), rangeFromTimestamp, rangeToTimestamp);
  }
}
