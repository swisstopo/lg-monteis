import type {
  ChartConfiguration,
  ChartDataset as ChartJsDataset,
  ChartOptions as ChartJsOptions,
} from 'chart.js';
import { ChartDataset, ChartOptions, ChartThemePalette, ChartType } from './chart.types';

type ChartJsScales = NonNullable<ChartJsOptions<ChartType>['scales']>;

export function buildChartConfig(
  type: ChartType,
  datasets: ChartDataset[],
  options: ChartOptions,
  palette: ChartThemePalette = {},
): ChartConfiguration<ChartType> {
  const safeDatasets = Array.isArray(datasets) ? datasets : [];

  const scales: ChartJsScales = {
    x: {
      type: options.xAxisType === 'time' ? 'time' : 'linear',
      // Keep the axis (and therefore the drag-zoom selection) pinned to the exact data/selection
      // range instead of Chart.js's default of expanding it to the nearest tick (e.g. whole days).
      bounds: 'data',
      time: {
        tooltipFormat: 'PPpp',
        displayFormats: {
          // Tell date-fns how to format specific zoom levels
          hour: 'MMM d, HH:mm', // e.g., "May 21, 14:00"
          day: 'MMM d', // e.g., "May 21"
          week: 'MMM d, yyyy',
          month: 'MMM yyyy',
        },
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
  };

  const uniqueYAxes = Array.from(new Set(safeDatasets.map((d) => d.yAxisId ?? 'y')));
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
      datasets: safeDatasets.map((dataset, i) => buildDataset(type, dataset, palette, i)),
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
        zoom: {
          zoom: {
            wheel: {
              enabled: false,
            },
            pinch: {
              enabled: false,
            },
            drag: {
              enabled: true,
              backgroundColor: 'rgba(66, 133, 244, 0.2)',
              borderWidth: 1,
              borderColor: 'rgba(66, 133, 244, 1)',
            },
            mode: 'xy',
          },
        },
      },
      scales: scales,
    },
  };
}

function buildDataset(
  type: ChartType,
  dataset: ChartDataset,
  palette: ChartThemePalette,
  index: number,
): ChartJsDataset<ChartType> {
  const paletteColors = palette.seriesColors?.length ? palette.seriesColors : ['#3366cc'];
  const autoColor = paletteColors[index % paletteColors.length];
  const color = dataset.color ?? autoColor;
  return {
    label: dataset.label,
    data: dataset.data,
    borderColor: color,
    backgroundColor: color,
    showLine: type === 'line',
    parsing: false, // performance optimization but requires data to match exact format
    yAxisID: dataset.yAxisId ?? 'y',
  } satisfies ChartJsDataset<ChartType>;
}
