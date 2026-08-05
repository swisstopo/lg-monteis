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
  yAxisId?: 'y' | 'y1';
}

export interface ChartOptions {
  title?: string;
  subtitle?: string;
  xAxisLabel?: string;
  yAxisLabel?: string;
  secondaryYAxisLabel?: string;
  showLegend?: boolean;
  maintainAspectRatio?: boolean;
}

export interface ChartPointEvent {
  datasetId: string | number;
  datasetLabel: string;
  point: ChartPoint;
}
