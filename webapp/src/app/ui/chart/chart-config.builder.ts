import type {
  ChartConfiguration,
  ChartDataset as ChartJsDataset,
  ChartOptions as ChartJsOptions,
} from 'chart.js';
import { ChartDataset, ChartOptions, ChartThemePalette, ChartType } from './chart.types';

export function buildChartConfig(
  type: ChartType,
  datasets: ChartDataset[],
  labels: (string | number)[],
  options: ChartOptions,
  locale: string = 'en-GB',
  palette: ChartThemePalette = {},
): ChartConfiguration<ChartType> {
  const isTimeSeries = type === 'scatter';
  const xSpanMs = isTimeSeries ? computeXSpanMs(datasets) : 0;

  const scales: any = {
    x: {
      type: isTimeSeries ? 'linear' : 'category',
      title: {
        display: !!options.xAxisLabel,
        text: options.xAxisLabel ?? '',
        color: palette.textColor,
      },
      ticks: {
        color: palette.textColor,
        ...(isTimeSeries
          ? {
              maxTicksLimit: 8,
              callback: (value: any) => formatDateTick(Number(value), xSpanMs, locale),
            }
          : {}),
      },
      grid: { color: palette.gridColor },
    },
    ...options.advancedOptions?.scales,
  };

  const uniqueYAxes = Array.from(new Set(datasets.map((d) => d.yAxisId ?? 'y')));
  uniqueYAxes.forEach((axisId, index) => {
    // Alternate sides: even indexes on the left, odd indexes on the right ??
    const position = index % 2 === 0 ? 'left' : 'right';

    // Only draw the background grid lines for the very first axis
    const drawGridLines = index === 0;

    // Check if a custom title was provided in the options map
    const axisTitle = options.yAxisLabels?.[axisId];

    scales[axisId] = {
      position: position,
      title: {
        display: !!axisTitle,
        text: axisTitle ?? '',
        color: palette.textColor,
      },
      ticks: {
        maxTicksLimit: 12,
        color: palette.textColor,
      },
      grid: {
        color: palette.gridColor,
        drawOnChartArea: drawGridLines, // Prevents overlapping grid lines!
      },
    };
  });

  return {
    type,
    data: {
      labels,
      datasets: datasets.map((dataset, i) => buildDataset(type, dataset, palette, i)),
    },
    options: {
      responsive: true,
      maintainAspectRatio: options.maintainAspectRatio ?? false,
      animation: false,
      ...options.advancedOptions,
      plugins: {
        title: {
          display: !!options.title,
          text: options.title ?? '',
          color: palette.textColor,
          position: 'top',
          align: 'center',
          font: {
            size: 18,
          },
        },
        subtitle: {
          display: !!options.subtitle,
          text: options.subtitle ?? '',
          color: palette.textColor,
          position: 'top',
          align: 'center',
          font: {
            size: 14,
          },
          padding: {
            bottom: 10,
          },
        },
        legend: {
          display: options.showLegend ?? true,
          position: 'bottom',
          align: 'start',
          labels: {
            usePointStyle: true,
            pointStyle: 'circle',
            color: palette.textColor,
            padding: 20,
          },
        },
        ...options.advancedOptions?.plugins,
      },
      scales: scales,
    } as ChartJsOptions<ChartType>,
  };
}

function computeXSpanMs(datasets: ChartDataset[]): number {
  const xValues = datasets.flatMap((dataset) => dataset.data.map((point) => point.x));
  if (xValues.length === 0) return 0;
  return Math.max(...xValues) - Math.min(...xValues);
}

function formatDateTick(value: number, spanMs: number, locale: string): string {
  const date = new Date(value);
  const hour = 60 * 60 * 1000;
  const day = 24 * hour;

  if (spanMs <= day) {
    return date.toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit' });
  }
  if (spanMs <= 3 * day) {
    return date.toLocaleDateString(locale, { month: 'short', day: 'numeric', hour: '2-digit' });
  }

  return date.toLocaleDateString(locale, { month: 'short', day: 'numeric' });
}

function buildDataset(
  type: ChartType,
  dataset: ChartDataset,
  palette: ChartThemePalette,
  index: number,
): ChartJsDataset<ChartType> {
  const paletteColors = palette.seriesColors ?? [];
  const autoColor = paletteColors[index % paletteColors.length];
  const color = dataset.color ?? autoColor;
  return {
    label: dataset.label,
    data: dataset.data,
    borderColor: color,
    backgroundColor: color,
    showLine: type === 'line',
    parsing: false,
    yAxisID: dataset.yAxisId ?? 'y',
  } satisfies ChartJsDataset;
}
