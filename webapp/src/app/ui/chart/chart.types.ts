export type ChartType = 'line' | 'scatter';

export interface ChartPoint {
  x: number;
  y: number;
}

export interface ChartDataset {
  id: string | number;
  label: string;
  data: ChartPoint[];
  color?: string;
}

export interface ChartOptions {
  xAxisLabel?: string;
  yAxisLabel?: string;
  showLegend?: boolean;
  maintainAspectRatio?: boolean;
}

export interface ChartPointEvent {
  datasetId: string | number;
  datasetLabel: string;
  point: ChartPoint;
}
