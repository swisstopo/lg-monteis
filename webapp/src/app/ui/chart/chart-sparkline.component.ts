import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  input,
  viewChild,
} from '@angular/core';
import { ChartConfiguration, Chart as ChartJs, TooltipModel } from 'chart.js';
import { format } from 'date-fns';
import { registerChartJs } from './chart-registry';
import { resolveThemePalette } from './chart-theme.util';
import { ChartPoint } from './chart.types';

registerChartJs();

/**
 * A minimal, axis-less line chart for inline use (e.g. one ag-grid cell) such as a "last N days"
 * trend indicator. Unlike {@link ChartComponent}, this has no zoom/toolbar/legend chrome - just
 * the line and a hover tooltip.
 *
 * The default canvas-drawn tooltip is disabled in favor of an `external` one appended to
 * `document.body` with `position: fixed`: a tooltip wide enough to show a timestamp would
 * otherwise be clipped both by this chart's own tiny canvas (a tooltip can never draw outside its
 * own canvas) and by an ancestor with `overflow: hidden` (e.g. an ag-grid cell).
 */
@Component({
  selector: 'app-chart-sparkline',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<canvas #canvas></canvas>',
  styles: `
    :host {
      display: block;
      width: 100%;
      height: 100%;
    }
    canvas {
      width: 100%;
      height: 100%;
    }
  `,
})
export class ChartSparklineComponent {
  private readonly canvasRef = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private readonly destroyRef = inject(DestroyRef);

  readonly data = input<ChartPoint[]>([]);

  private chart?: ChartJs<'line', ChartPoint[]>;
  private tooltipEl?: HTMLDivElement;
  private viewReady = false;

  constructor() {
    afterNextRender(() => {
      this.viewReady = true;
      this.render();
    });
    this.destroyRef.onDestroy(() => {
      this.chart?.destroy();
      this.tooltipEl?.remove();
    });

    effect(() => {
      this.data();
      if (this.viewReady) this.render();
    });
  }

  private render(): void {
    const canvas = this.canvasRef().nativeElement;
    const config = this.buildConfig(this.data(), canvas);

    if (this.chart) {
      this.chart.data = config.data;
      this.chart.options = config.options ?? {};
      this.chart.update('none');
    } else {
      this.chart = new ChartJs(canvas, config);
    }
  }

  private buildConfig(
    data: ChartPoint[],
    canvas: HTMLCanvasElement,
  ): ChartConfiguration<'line', ChartPoint[]> {
    const color = resolveThemePalette(canvas).seriesColors?.[0] ?? '#4285F4';

    return {
      type: 'line',
      data: {
        datasets: [
          {
            data,
            borderColor: color,
            backgroundColor: color,
            borderWidth: 1.5,
            pointRadius: 0,
            pointHoverRadius: 3,
            parsing: false,
            normalized: true,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        interaction: { mode: 'nearest', intersect: false },
        scales: {
          x: { type: 'time', display: false },
          y: { display: false },
        },
        plugins: {
          legend: { display: false },
          tooltip: {
            enabled: false,
            external: (context) => this.renderExternalTooltip(context.tooltip, canvas),
          },
        },
      },
    };
  }

  /**
   * Positions a `position: fixed` HTML tooltip off the canvas's bounding rect instead of relying
   * on Chart.js's default canvas-drawn tooltip - see the class doc for why.
   */
  private renderExternalTooltip(tooltip: TooltipModel<'line'>, canvas: HTMLCanvasElement): void {
    if (tooltip.opacity === 0) {
      if (this.tooltipEl) this.tooltipEl.style.opacity = '0';
      return;
    }

    const el = this.getOrCreateTooltipElement();
    const point = tooltip.dataPoints?.[0];
    if (point) {
      const date = format(point.parsed.x as number, 'yyyy-MM-dd HH:mm');
      const value = (point.parsed.y as number).toFixed(2);
      el.textContent = `${date} — ${value}`;
    }

    const rect = canvas.getBoundingClientRect();
    el.style.opacity = '1';
    el.style.left = `${rect.left + tooltip.caretX}px`;
    el.style.top = `${rect.top + tooltip.caretY - el.offsetHeight - 8}px`;
  }

  private getOrCreateTooltipElement(): HTMLDivElement {
    if (this.tooltipEl) return this.tooltipEl;

    const el = document.createElement('div');
    el.style.position = 'fixed';
    el.style.pointerEvents = 'none';
    el.style.zIndex = '10000';
    el.style.padding = '4px 8px';
    el.style.borderRadius = '4px';
    el.style.fontSize = '12px';
    el.style.whiteSpace = 'nowrap';
    el.style.opacity = '0';
    el.style.transition = 'opacity 0.1s ease';
    el.style.background = 'var(--mat-sys-surface-container-highest)';
    el.style.color = 'var(--mat-sys-on-surface)';
    el.style.boxShadow = '0 1px 4px rgba(0, 0, 0, 0.3)';
    document.body.appendChild(el);
    this.tooltipEl = el;
    return el;
  }
}
