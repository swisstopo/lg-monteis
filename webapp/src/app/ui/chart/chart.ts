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
import {
  ChartDataset,
  ChartOptions,
  ChartPointEvent,
  ChartThemePalette,
  ChartType,
} from './chart.types';

registerChartJs();

function resolveColorScheme(element: Element): 'light' | 'dark' {
  const scheme = getComputedStyle(element).colorScheme;
  if (scheme === 'dark') return 'dark';
  if (scheme === 'light') return 'light';
  // `color-scheme: light dark` defers the choice to the user's OS preference.
  return matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

/**
 * Splits `light-dark(<light>, <dark>)` into its two arguments, respecting nested parentheses
 * (e.g. `light-dark(rgb(0, 0, 0), rgb(255, 255, 255))`) rather than naively splitting on the
 * first comma.
 */
function parseLightDark(value: string): [light: string, dark: string] | undefined {
  const prefix = 'light-dark(';
  if (!value.startsWith(prefix) || !value.endsWith(')')) return undefined;

  const args = value.slice(prefix.length, -1);
  let depth = 0;
  for (let i = 0; i < args.length; i++) {
    const char = args[i];
    if (char === '(') depth++;
    else if (char === ')') depth--;
    else if (char === ',' && depth === 0) {
      return [args.slice(0, i).trim(), args.slice(i + 1).trim()];
    }
  }
  return undefined;
}

function resolveCssVariableColor(element: HTMLElement, variableName: string): string | undefined {
  // `--mat-sys-*` tokens are defined as `light-dark(<light>, <dark>)`. Reading a custom property's
  // computed value returns that raw text as-is (functions inside custom properties are only
  // evaluated once consumed by an actual property), so we parse it and pick the matching branch
  // ourselves based on the resolved `color-scheme`.
  const raw = getComputedStyle(element).getPropertyValue(variableName).trim();
  if (!raw) return undefined;

  const parsed = parseLightDark(raw);
  if (!parsed) return raw;

  const [lightValue, darkValue] = parsed;
  return resolveColorScheme(element) === 'dark' ? darkValue : lightValue;
}

// seriesColor: resolveCssVariableColor(element, '--mat-sys-primary'), TODO replace with palett
function resolveThemePalette(element: HTMLElement): ChartThemePalette {
  return {
    textColor: resolveCssVariableColor(element, '--mat-sys-on-surface'),
    gridColor: resolveCssVariableColor(element, '--mat-sys-surface-container-highest'),
    seriesColors: [
      '#4285F4',
      '#EA4335',
      '#FBBC04',
      '#34A853',
      '#990099',
      '#00BCD4',
      '#FF6D00',
      '#E91E63',
    ],
  };
}

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
  readonly type = input<ChartType>('line');
  readonly datasets = input<ChartDataset[]>([]);
  readonly labels = input<(string | number)[]>([]);
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
    });
    // key architectural consideration for hydration saftey -> afterNextRender
    afterNextRender(() => {
      const canvasEl = this.canvasRef().nativeElement;

      this.palette.set(resolveThemePalette(canvasEl));
      this.viewReady.set(true);

      const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
      const themeChangeListener = () => {
        this.palette.set(resolveThemePalette(canvasEl));
      };

      mediaQuery.addEventListener('change', themeChangeListener);

      // Clean up the listener to prevent memory leaks
      this.destroyRef.onDestroy(() => {
        mediaQuery.removeEventListener('change', themeChangeListener);
      });
    });

    effect(() => {
      if (!this.viewReady()) {
        return;
      }
      const config = buildChartConfig(
        this.type(),
        this.datasets(),
        this.labels(),
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
