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
  maintainAspectRatio?: boolean;
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
}
