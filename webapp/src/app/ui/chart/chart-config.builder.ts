import type { ChartConfiguration, ChartDataset as ChartJsDataset } from 'chart.js';
import { ChartDataset, ChartOptions, ChartType } from './chart.types';

export interface ChartThemePalette {
  textColor?: string;
  gridColor?: string;
  seriesColor?: string;
}

export function buildChartConfig(
  type: ChartType,
  datasets: ChartDataset[],
  labels: (string | number)[],
  options: ChartOptions,
  palette: ChartThemePalette = {},
): ChartConfiguration {
  const isTimeSeries = type === 'scatter';
  const xSpanMs = isTimeSeries ? computeXSpanMs(datasets) : 0;
  const hasSecondaryYAxis = datasets.some((dataset) => dataset.yAxisId === 'y1');

  return {
    type,
    data: {
      labels,
      datasets: datasets.map((dataset) => buildDataset(type, dataset, palette)),
    },
    options: {
      responsive: true,
      maintainAspectRatio: options.maintainAspectRatio ?? false,
      animation: false,
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
      },
      scales: {
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
                  callback: (value) => formatDateTick(Number(value), xSpanMs),
                }
              : {}),
          },
          grid: { color: palette.gridColor },
        },
        y: {
          title: {
            display: !!options.yAxisLabel,
            text: options.yAxisLabel ?? '',
            color: palette.textColor,
          },
          ticks: { maxTicksLimit: 12, color: palette.textColor },
          grid: { color: palette.gridColor },
        },
        ...(hasSecondaryYAxis
          ? {
              y1: {
                position: 'right' as const,
                title: {
                  display: !!options.secondaryYAxisLabel,
                  text: options.secondaryYAxisLabel ?? '',
                  color: palette.textColor,
                },
                ticks: { maxTicksLimit: 12, color: palette.textColor },
                // Avoid overlaying gridlines from both y-axes on top of each other.
                grid: { drawOnChartArea: false },
              },
            }
          : {}),
      },
    },
  };
}

function computeXSpanMs(datasets: ChartDataset[]): number {
  const xValues = datasets.flatMap((dataset) => dataset.data.map((point) => point.x));
  if (xValues.length === 0) return 0;
  return Math.max(...xValues) - Math.min(...xValues);
}

function formatDateTick(value: number, spanMs: number): string {
  const date = new Date(value);
  const hour = 60 * 60 * 1000;
  const day = 24 * hour;

  if (spanMs <= day) {
    // TODO use locale
    return date.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
  }
  if (spanMs <= 3 * day) {
    // TODO use locale
    return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric', hour: '2-digit' });
  }
  // TODO use locale
  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

function buildDataset(
  type: ChartType,
  dataset: ChartDataset,
  palette: ChartThemePalette,
): ChartJsDataset {
  const color = dataset.color ?? palette.seriesColor;
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
