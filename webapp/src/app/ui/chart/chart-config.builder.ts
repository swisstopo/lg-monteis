import type { ChartConfiguration, ChartDataset as ChartJsDataset } from 'chart.js';
import { ChartDataset, ChartOptions, ChartType } from './chart.types';

export function buildChartConfig(
  type: ChartType,
  datasets: ChartDataset[],
  labels: (string | number)[],
  options: ChartOptions,
): ChartConfiguration {
  return {
    type,
    data: {
      labels,
      datasets: datasets.map((dataset) => buildDataset(type, dataset)),
    },
    options: {
      responsive: true,
      maintainAspectRatio: options.maintainAspectRatio ?? false,
      animation: false,
      plugins: {
        legend: { display: options.showLegend ?? true },
      },
      scales: {
        x: {
          type: type === 'scatter' ? 'linear' : 'category',
          title: { display: !!options.xAxisLabel, text: options.xAxisLabel ?? '' },
        },
        y: {
          title: { display: !!options.yAxisLabel, text: options.yAxisLabel ?? '' },
        },
      },
    },
  };
}

function buildDataset(type: ChartType, dataset: ChartDataset): ChartJsDataset {
  return {
    label: dataset.label,
    data: dataset.data,
    borderColor: dataset.color,
    backgroundColor: dataset.color,
    showLine: type === 'line',
    parsing: false,
  } as ChartJsDataset;
}
