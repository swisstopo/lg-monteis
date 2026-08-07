import type {
  ChartConfiguration,
  ChartDataset as ChartJsDataset,
  ChartOptions as ChartJsOptions,
} from 'chart.js';
import { ChartDataset, ChartOptions, ChartThemePalette, ChartType } from './chart.types';

export function buildChartConfig(
  type: ChartType,
  datasets: ChartDataset[],
  options: ChartOptions,
  locale: string = 'en-GB',
  palette: ChartThemePalette = {},
): ChartConfiguration<ChartType> {
  datasets = Array.isArray(datasets) ? datasets : [];

  const scales: any = {
    x: {
      type: options.xAxisType === 'time' ? 'time' : 'linear',
      time: {
        tooltipFormat: 'PPpp',
      },
      title: {
        display: !!options.xAxisLabel,
        text: options.xAxisLabel ?? '',
        color: palette.textColor,
      },
      ticks: {
        color: palette.textColor,
        maxTicksLimit: 8,
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
        maxTicksLimit: 8,
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
