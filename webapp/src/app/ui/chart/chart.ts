import { DatePipe } from '@angular/common';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  input,
  OnDestroy,
  output,
  viewChild,
} from '@angular/core';
import { MatButton } from '@angular/material/button';
import {
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ActiveElement, ChartConfiguration, ChartEvent, Chart as ChartJs } from 'chart.js';
import { buildChartConfig } from './chart-config.builder';
import { registerChartJs } from './chart-registry';
import { ChartDataset, ChartOptions, ChartPointEvent, ChartType } from './chart.types';

registerChartJs();

@Component({
  selector: 'app-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './chart.html',
  styleUrl: './chart.scss',
  imports: [
    MatDialogContent,
    MatDialogTitle,
    MatDialogActions,
    MatButton,
    MatDialogClose,
    MatIcon,
    TranslatePipe,
  ],
  providers: [DatePipe],
})
export default class Chart implements OnDestroy {
  private readonly i18nService = inject(TranslateService);
  private readonly datePipe = inject(DatePipe);
  readonly dialogRef = inject<MatDialogRef<Chart>>(MatDialogRef, {
    optional: true,
  });
  private readonly canvasRef = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  readonly type = input<ChartType>('line');
  readonly datasets = input<ChartDataset[]>([]);
  readonly labels = input<(string | number)[]>([]);
  readonly options = input<ChartOptions>({});
  readonly sensorName = input<string | undefined>();
  readonly rangeFrom = input<Date | undefined>();
  readonly rangeTo = input<Date | undefined>();

  readonly pointClick = output<ChartPointEvent>();
  readonly pointHover = output<ChartPointEvent>();

  private instance?: ChartJs;

  constructor() {
    afterNextRender(() => this.createChart());
    effect(() => {
      const config = buildChartConfig(this.type(), this.datasets(), this.labels(), this.options());
      this.updateChart(config);
    });
  }

  ngOnDestroy(): void {
    this.instance?.destroy();
  }

  readonly title = computed(() =>
    this.i18nService.translate('chart.title', {
      name: this.sensorName(),
      rangeFrom: this.datePipe.transform(this.rangeFrom(), 'yyyy-MM-dd'),
      rangeTo: this.datePipe.transform(this.rangeTo(), 'yyyy-MM-dd'),
    })(),
  );

  private createChart(): void {
    const config = buildChartConfig(this.type(), this.datasets(), this.labels(), this.options());
    this.instance = new ChartJs(this.canvasRef().nativeElement, {
      ...config,
      options: {
        ...config.options,
        onClick: (event, elements) => this.emitPointEvent(event, elements, this.pointClick),
        onHover: (event, elements) => this.emitPointEvent(event, elements, this.pointHover),
      },
    });
  }

  private updateChart(config: ChartConfiguration): void {
    if (!this.instance) {
      return;
    }
    (this.instance.config as ChartConfiguration).type = config.type;
    this.instance.data = config.data;
    this.instance.options = {
      ...config.options,
      onClick: this.instance.options.onClick,
      onHover: this.instance.options.onHover,
    };
    this.instance.update();
  }

  private emitPointEvent(
    _event: ChartEvent,
    elements: ActiveElement[],
    emitter: typeof this.pointClick,
  ): void {
    const element = elements[0];
    if (!element || !this.instance) {
      return;
    }
    const dataset = this.datasets()[element.datasetIndex];
    const point = dataset?.data[element.index];
    if (!dataset || !point) {
      return;
    }
    emitter.emit({ datasetId: dataset.id, datasetLabel: dataset.label, point });
  }
}
