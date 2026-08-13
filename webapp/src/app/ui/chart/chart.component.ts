import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { MatButton } from '@angular/material/button';
import {
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { TranslatePipe } from '@ngx-translate/core';
import { ActiveElement, ChartConfiguration, ChartEvent, Chart as ChartJs } from 'chart.js';
import { buildChartConfig } from './chart-config.builder';
import { registerChartJs } from './chart-registry';
import { resolveThemePalette } from './chart-theme.util';
import { ChartToolbarComponent } from './chart-toolbar/chart-toolbar.component';
import {
  ChartDataset,
  ChartOptions,
  ChartPointEvent,
  ChartRangeEvent,
  ChartThemePalette,
  ChartType,
} from './chart.types';

registerChartJs();

/**
 * This component renders line or scatter charts.
 *
 * Minimal required data:
 * - title: Renders dialog title
 * - datasets: ChartDataset[] - array of datasets to render
 * - options: ChartOptions - see @ChartTypes createTimeChartOptions | createLinearChartOptions
 *
 * Outputs:
 * - pointClick: ChartPointEvent - emitted when a point is clicked
 * - pointHover: ChartPointEvent - emitted when a point is hovered
 * - rangeSelected: ChartRangeEvent - emitted when a range is selected
 *
 * Zooming always refetches data from the backend for the selected x-Axis range. Y-Axis zooming is
 * handled by the plugin's default drag behaviour. The initial range is stored (and resettable.
 *
 * To render a line chart, you need to provide:
 * - datasets: ChartDataset[] {
 *     id: 'taupe_sensor_1',
 *     label: 'Taupe Sensor 1',
 *     data: [
 *       { x: 10, y: 2.4 },
 *       { x: 25, y: 2.8 },
 *       { x: 50, y: 3.1 },
 *       { x: 120, y: 4.5 },
 *     ],
 *     yAxisId: 'y',
 *   };
 * - options: ChartOptions = {
 *       title: 'Taupe Cable Analysis',
 *       xAxisLabel: 'Taupe cable length [cm]',
 *       xAxisType: 'linear',
 *       yAxisLabels: {
 *         y: 'Relative Electric Permitivity',
 *       },
 *     };
 *
 * - type: 'line'
 *
 **/
@Component({
  selector: 'app-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './chart.component.html',
  styleUrl: './chart.component.scss',
  imports: [
    MatDialogContent,
    MatDialogTitle,
    MatDialogActions,
    MatButton,
    MatDialogClose,
    MatIcon,
    MatProgressSpinner,
    TranslatePipe,
    ChartToolbarComponent,
  ],
})
export class ChartComponent {
  private readonly destroyRef = inject(DestroyRef);
  private readonly canvasRef = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  readonly title = input.required<string>();
  readonly type = input<ChartType>('scatter');
  readonly datasets = input<ChartDataset[]>([]);
  readonly options = input<ChartOptions>({});

  readonly pointClick = output<ChartPointEvent>();
  readonly pointHover = output<ChartPointEvent>();
  readonly rangeSelected = output<ChartRangeEvent>();

  private instance?: ChartJs;
  private lastHoverKey?: string;
  private readonly palette = signal<ChartThemePalette>({});
  private readonly viewReady = signal(false);
  /**
   * Y axes zoomed via the plugin's default drag behaviour.
   * Since every dataset/options update rebuilds `scales` from scratch, these are re-applied on
   * each render so the Y zoom survives the data refresh triggered by that X refetch.
   */
  private manualYRanges: Record<string, { min: number; max: number }> = {};
  private initialDateRange?: { min: number; max: number };
  /**
   * Set whenever the current initial range needs to be (re-)captured from the next unzoomed
   * dataset render, i.e. on first load and after an explicit resetZoom(). This must not be tied
   * to `manualYRanges` emptiness: X-only zooms (zoomIn/zoomOut/drag) never touch `manualYRanges`,
   * but they do trigger a dataset refetch, which would otherwise overwrite the true initial range
   * with the narrowed one and make zoomOut() unable to expand past the current view.
   */
  private captureInitialRange = true;
  private resetRange?: { min: number; max: number };
  readonly dragZoomEnabled = signal(true);
  private pendingZoomAnimation = false;

  constructor() {
    // key architectural consideration for for gpu references clean up
    this.destroyRef.onDestroy(() => {
      this.instance?.destroy();
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
      if (!this.viewReady() || this.datasets().length === 0) {
        return;
      }
      const config = buildChartConfig(this.type(), this.datasets(), this.options(), this.palette());
      this.applyDragZoom(config);
      this.applyManualYRanges(config);
      if (this.captureInitialRange) {
        this.initialDateRange = this.computeDateRange(this.datasets()); // TODO
        this.captureInitialRange = false;
      }
      // Chart.js resolves dataset controllers at construction, so a type change requires a rebuild.
      if (this.instance && (this.instance.config as ChartConfiguration).type !== config.type) {
        this.instance.destroy();
        this.instance = undefined;
      }
      if (this.instance) {
        this.updateChart(config, this.pendingZoomAnimation ? 'zoom' : undefined);
        this.pendingZoomAnimation = false;
      } else {
        this.createChart(config);
        this.pendingZoomAnimation = false;
      }
    });
  }

  /**
   * toolbar actions
   */

  public resetZoom(): void {
    this.pendingZoomAnimation = true;
    this.resetRange = this.initialDateRange;
    if (this.instance) {
      this.instance.resetZoom();
    }
    this.manualYRanges = {};
    this.captureInitialRange = true;
  }

  private readonly zoomFactor = 2;

  public zoomIn(): void {
    this.pendingZoomAnimation = true;
    this.zoomRange(1 / this.zoomFactor);
  }

  public zoomOut(): void {
    this.pendingZoomAnimation = true;
    this.zoomRange(this.zoomFactor);
  }

  public toggleDragZoom(): void {
    this.dragZoomEnabled.update((enabled) => !enabled);
  }

  public downloadChart(): void {
    if (!this.instance) {
      return;
    }
    const image = this.instance.toBase64Image('image/png');
    const link = document.createElement('a');
    link.href = image;
    link.download = `${this.title()}.png`;
    link.click();
  }

  private zoomRange(factor: number): void {
    if (!this.instance || !this.initialDateRange) {
      return;
    }
    const xScale = this.instance.scales['x'];
    if (!xScale) {
      return;
    }
    const currentMin = xScale.min;
    const currentMax = xScale.max;
    const center = (currentMin + currentMax) / 2;
    const newRange = (currentMax - currentMin) * factor;
    const halfRange = newRange / 2;

    const min = Math.max(this.initialDateRange.min, center - halfRange);
    const max = Math.min(this.initialDateRange.max, center + halfRange);

    this.rangeSelected.emit({ min, max });
  }

  private createChart(config: ChartConfiguration): void {
    const canvas = this.canvasRef().nativeElement;
    this.instance = new ChartJs(canvas, this.withEventHandlers(config));
  }

  /**
   * Updates the chart configuration
   *     For real-time streaming - if ever a requirement we will need to update the inner array directly:
   *     this.instance.data.datasets[i].data.push(newPoint);
   *     this.instance.data.labels.push(newLabel);
   *     this.instance.update('quiet'); // 'quiet' skips animations
   */
  private updateChart(config: ChartConfiguration, mode?: 'zoom'): void {
    if (!this.instance) {
      return;
    }
    const next = this.withEventHandlers(config);

    // Mutate the properties of the existing data object to prevent complete rerendering
    (this.instance.config as ChartConfiguration).type = next.type;
    this.instance.data.labels = next.data.labels;
    this.instance.data.datasets = next.data.datasets;

    this.instance.options = next.options ?? {};
    this.instance.update(mode);
  }

  /**
   * Chart.js re-resolves `chart.options` from `chart.config.options` on every `update()`, so
   * handlers assigned onto the resolved options object are discarded. They must live in the
   * config itself to survive updates.
   */
  private withEventHandlers(config: ChartConfiguration): ChartConfiguration {
    const plugins = config.options?.plugins ?? {};
    return {
      ...config,
      options: {
        ...config.options,
        onClick: (event, elements) => this.emitPointEvent(event, elements, this.pointClick),
        onHover: (event, elements) => this.emitHoverEvent(event, elements),
        plugins: {
          ...plugins,
          zoom: {
            ...plugins.zoom,
            zoom: {
              ...plugins.zoom?.zoom,
              onZoomComplete: ({ chart }: { chart: ChartJs }) => this.handleZoomComplete(chart),
            },
          },
        },
      },
    };
  }

  /**
   * By the time `onZoomComplete` fires, the drag-zoom plugin has already applied its default
   * behaviour to every axis matched by `mode: 'xy'` (one `chart.update()`, already paid for).
   * The Y axes are left as the plugin zoomed them (and remembered in `manualYRanges` so they
   * survive future re-renders); the X axis's resulting range is reported via `rangeSelected`
   * instead, for the consumer to refetch, rather than kept as a client-side-only zoom.
   */
  private handleZoomComplete(chart: ChartJs): void {
    this.pendingZoomAnimation = true;
    if (this.resetRange) {
      this.rangeSelected.emit(this.resetRange);
      this.resetRange = undefined;
      return;
    }
    const xScale = chart.scales['x'];
    if (!xScale) {
      return;
    }
    Object.values(chart.scales)
      .filter((scale) => scale.axis === 'y')
      .forEach((scale) => {
        this.manualYRanges[scale.id] = { min: scale.min, max: scale.max };
      });
    this.rangeSelected.emit({ min: xScale.min, max: xScale.max });
  }

  //
  private computeDateRange(datasets: ChartDataset[]): { min: number; max: number } | undefined {
    let min = Infinity;
    let max = -Infinity;
    let hasData = false;

    for (const dataset of datasets) {
      const data = dataset.data;
      if (data.length === 0) continue;

      // Assuming time-series data is sorted from the backend (Fastest O(D) execution)
      const localMin = data[0].x;
      const localMax = data[data.length - 1].x;

      if (localMin < min) min = localMin;
      if (localMax > max) max = localMax;
      hasData = true;
    }

    return hasData ? { min, max } : undefined;
  }

  private applyManualYRanges(config: ChartConfiguration): void {
    const scales = config.options?.scales as Record<string, { min?: number; max?: number }>;
    if (!scales) {
      return;
    }
    for (const [axisId, range] of Object.entries(this.manualYRanges)) {
      if (scales[axisId]) {
        scales[axisId].min = range.min;
        scales[axisId].max = range.max;
      }
    }
  }

  private applyDragZoom(config: ChartConfiguration): void {
    const zoomOptions = config.options?.plugins?.zoom?.zoom;
    if (!zoomOptions || typeof zoomOptions !== 'object') {
      return;
    }
    (zoomOptions as { drag?: { enabled?: boolean } }).drag = {
      ...(zoomOptions as { drag?: object }).drag,
      enabled: this.dragZoomEnabled(),
    };
  }

  /**
   * Chart.js fires `onHover` on every mousemove, so repeated events for the same point are
   * filtered out to avoid needlessly notifying consumers.
   */
  private emitHoverEvent(event: ChartEvent, elements: ActiveElement[]): void {
    const element = elements[0];
    const key = element ? `${element.datasetIndex}:${element.index}` : undefined;
    if (key === this.lastHoverKey) {
      return;
    }
    this.lastHoverKey = key;
    this.emitPointEvent(event, elements, this.pointHover);
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
