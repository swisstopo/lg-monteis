import { DatePipe } from '@angular/common';
import { Component, effect, inject, inputBinding, outputBinding, signal } from '@angular/core';
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
import { ChartDataset } from 'chart.js';
import { OverviewControllerService, ReadSimpleMetricDto } from '../../../core/generated';
import { ChartComponent, ChartOptions, ChartRangeEvent } from '../../../ui/chart';
import {
  generateMockHumidityDataset,
  generateMockPressureDataset,
  generateMockStressRadialDataset,
  generateMockTemperatureDataset,
  generateTaupeDataset,
} from '../../../ui/chart/chart-data-mock';
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
    const start = this.dateRangeModel().start!;
    const end = this.dateRangeModel().end!;

    start.setHours(0, 0, 0, 0);
    end.setHours(23, 59, 59, 999);

    this.plottedIds.set([1, 2, 3, 5]);
    this.fetchChartData(start, end);

    const title = this.i18nService.translate('chart.title', {
      name: 'MyFancyExperiment',
      rangeFrom: this.datePipe.transform(start),
      rangeTo: this.datePipe.transform(end),
    })();

    this.dialog.open(ChartComponent, {
      width: '95vw',
      maxWidth: '100%',
      height: '95vh',
      maxHeight: '100%',
      autoFocus: true,
      bindings: [
        inputBinding('title', () => title),
        inputBinding('datasets', () => this.mesurementsService.chartData.value()?.datasets ?? []),
        inputBinding('options', (): ChartOptions => ({
          title: title,
          subtitle: 'Plot of measurements',
          xAxisType: 'time',
          xAxisLabel: 'Date',
          yAxisLabels: this.mesurementsService.chartData.value()?.yAxisLabels ?? {},
        })),
        outputBinding('rangeSelected', (range) => this.onRangeSelected(<ChartRangeEvent>range)),
      ],
    });
  }

  private onRangeSelected(range: ChartRangeEvent): void {
    this.fetchChartData(new Date(range.min), new Date(range.max));
  }

  private fetchChartData(start: Date, end: Date): void {
    const isoFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSZZZZZ";
    const rangeFromTimestamp = this.datePipe.transform(start, isoFormat)!;
    const rangeToTimestamp = this.datePipe.transform(end, isoFormat)!;

    this.mesurementsService.getChartData(this.plottedIds(), rangeFromTimestamp, rangeToTimestamp);
  }

  //TODO remove
  protected onPlotMock() {
    const rangeFrom = this.datePipe.transform('2026-05-20');
    const rangeTo = this.datePipe.transform('2026-06-24');
    const datasets: ChartDataset[] = [
      generateMockPressureDataset(),
      generateMockStressRadialDataset(),
      generateMockTemperatureDataset(),
      generateMockHumidityDataset(),
    ];
    const title = this.i18nService.translate('chart.title', {
      name: 'MyFancyExperiment',
      rangeFrom: rangeFrom,
      rangeTo: rangeTo,
    })();
    const options: ChartOptions = {
      title: title,
      subtitle: 'Plot of 666 measurements',
      xAxisType: 'time',
      xAxisLabel: 'Date',
      yAxisLabels: {
        y: 'Fluid Pressure [kPa]',
        y2: 'Radial Stress [bar]',
        y3: 'Temperature [°C]',
        y4: 'Relative Humidity [%]',
      },
    };

    this.dialog.open(ChartComponent, {
      width: '95vw',
      maxWidth: '100%',
      height: '95vh',
      maxHeight: '100%',
      autoFocus: true,
      bindings: [
        inputBinding('title', () => title),
        inputBinding('datasets', () => datasets),
        inputBinding('options', () => options),
        outputBinding('pointClick', (event) => console.log('click: ', event)),
        outputBinding('pointHover', (event) => console.log('hover: ', event)),
      ],
    });
  }

  //TODO remove
  protected onPlotLine() {
    const taupeOptions: ChartOptions = {
      title: 'Taupe Cable Analysis',
      xAxisLabel: 'Taupe cable length [cm]',
      yAxisLabels: {
        y: 'Relative Electric Permitivity',
      },
    };

    this.dialog.open(ChartComponent, {
      width: '95vw',
      maxWidth: '100%',
      height: '95vh',
      maxHeight: '100%',
      autoFocus: true,
      bindings: [
        inputBinding('title', () => 'TaupeProfile on 2026-05-20'),
        inputBinding('type', () => 'line'),
        inputBinding('datasets', () => [generateTaupeDataset()]),
        inputBinding('options', () => taupeOptions),
        outputBinding('pointClick', (event) => console.log('click: ', event)),
        outputBinding('pointHover', (event) => console.log('hover: ', event)),
      ],
    });
  }
}
