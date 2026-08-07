import { DatePipe } from '@angular/common';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  input,
  LOCALE_ID,
  output,
  signal,
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
import { TranslatePipe } from '@ngx-translate/core';
import { ActiveElement, ChartConfiguration, ChartEvent, Chart as ChartJs } from 'chart.js';
import { buildChartConfig } from './chart-config.builder';
import { registerChartJs } from './chart-registry';
import { resolveThemePalette } from './chart-theme.util';
import {
  ChartDataset,
  ChartOptions,
  ChartPointEvent,
  ChartThemePalette,
  ChartType,
} from './chart.types';

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
export default class Chart {
  private readonly locale = inject(LOCALE_ID);
  readonly dialogRef = inject<MatDialogRef<Chart>>(MatDialogRef, {
    optional: true,
  });
  private readonly destroyRef = inject(DestroyRef);
  private readonly canvasRef = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  readonly title = input.required<string>();
  readonly type = input<ChartType>('scatter');
  readonly datasets = input<ChartDataset[]>([]);
  readonly options = input<ChartOptions>({});

  readonly pointClick = output<ChartPointEvent>();
  readonly pointHover = output<ChartPointEvent>();

  private instance?: ChartJs;
  private readonly palette = signal<ChartThemePalette>({});
  private readonly viewReady = signal(false);

  constructor() {
    // key architectural consideration for for gpu references clean up
    this.destroyRef.onDestroy(() => {
      this.instance?.destroy();
      this.dialogRef?.close();
    });
    // key architectural consideration for hydration saftey -> afterNextRender
    afterNextRender(() => {
      const canvasEl = this.canvasRef().nativeElement;

      this.palette.set(resolveThemePalette(canvasEl));
      this.viewReady.set(true);
      if (typeof window.matchMedia === 'function') {
        const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
        const themeChangeListener = () => {
          this.palette.set(resolveThemePalette(canvasEl));
        };

        mediaQuery.addEventListener('change', themeChangeListener);

        // Clean up the listener to prevent memory leaks
        this.destroyRef.onDestroy(() => {
          mediaQuery.removeEventListener('change', themeChangeListener);
        });
      }
    });

    effect(() => {
      if (!this.viewReady()) {
        return;
      }
      const config = buildChartConfig(
        this.type(),
        this.datasets(),
        this.options(),
        this.locale,
        this.palette(),
      );
      if (this.instance) {
        this.updateChart(config);
      } else {
        this.createChart(config);
      }
    });
  }

  public resetZoom(): void {
    if (this.instance) {
      this.instance.resetZoom();
    }
  }

  private createChart(config: ChartConfiguration): void {
    const canvas = this.canvasRef().nativeElement;
    this.instance = new ChartJs(canvas, this.withEventHandlers(config));
  }

  /**
   * Updates the chart configuration
   *     // For real-time streaming - if ever a requirement we will need to update the inner array directly:
   *     // this.instance.data.datasets[i].data.push(newPoint);
   *     // this.instance.data.labels.push(newLabel);
   *     // this.instance.update('quiet'); // 'quiet' skips animations
   */
  private updateChart(config: ChartConfiguration): void {
    if (!this.instance) {
      return;
    }
    const next = this.withEventHandlers(config);
    (this.instance.config as ChartConfiguration).type = next.type;
    // Mutate the properties of the existing data object to prevent complete rerendering
    this.instance.data.labels = next.data.labels;
    this.instance.data.datasets = next.data.datasets;

    this.instance.options = next.options ?? {};
    this.instance.update();
  }

  /**
   * Chart.js re-resolves `chart.options` from `chart.config.options` on every `update()`, so
   * handlers assigned onto the resolved options object are discarded. They must live in the
   * config itself to survive updates.
   */
  private withEventHandlers(config: ChartConfiguration): ChartConfiguration {
    return {
      ...config,
      options: {
        ...config.options,
        onClick: (event, elements) => this.emitPointEvent(event, elements, this.pointClick),
        onHover: (event, elements) => this.emitPointEvent(event, elements, this.pointHover),
      },
    };
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
