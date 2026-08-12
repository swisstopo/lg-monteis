export type ChartType = 'line' | 'scatter';

export interface ChartPoint {
  x: number;
  y: number;
}

export interface ChartDataset {
  id: string | number;
  label: string;
  data: ChartPoint[]; // requires presorted data as parsing is disabled
  color?: string;
  yAxisId?: string;
}

export interface ChartOptions {
  title?: string;
  subtitle?: string;
  xAxisLabel?: string;
  xAxisType?: 'linear' | 'time';
  yAxisLabels?: Record<string, string>;
  showLegend?: boolean;
}

export interface ChartPointEvent {
  datasetId: string | number;
  datasetLabel: string;
  point: ChartPoint;
}

export interface ChartRangeEvent {
  min: number;
  max: number;
}

export interface ChartThemePalette {
  textColor?: string;
  gridColor?: string;
  seriesColors?: string[];
  dragBorderColor?: string;
  dragBackgroundColor?: string;
}

export interface TimeSeriesOptions {
  title: string;
  xAxisLabel?: string;
  yAxisLabels: Record<string, string>;
  subtitle?: string;
  showLegend?: boolean;
}

export function createTimeChartOptions(input: TimeSeriesOptions): ChartOptions {
  return {
    ...input,
    xAxisType: 'time',
    showLegend: input.showLegend ?? true,
  };
}

export interface LinearSeriesOptions {
  title: string;
  xAxisLabel?: string;
  yAxisLabels: Record<string, string>;
  subtitle?: string;
  showLegend?: boolean;
}

export function createLinearChartOptions(input: LinearSeriesOptions): ChartOptions {
  return {
    ...input,
    xAxisType: 'linear',
    showLegend: input.showLegend ?? true,
  };
}
