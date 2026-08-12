import { describe, expect, it } from 'vitest';
import { buildChartConfig } from './chart-config.builder';
import { ChartDataset, ChartThemePalette } from './chart.types';

describe('buildChartConfig', () => {
  const dataset1: ChartDataset = {
    id: 'sensor-1',
    label: 'Sensor 1',
    data: [
      { x: 0, y: 1 },
      { x: 1, y: 2 },
    ],
    color: '#ff0000',
    yAxisId: 'y',
  };

  const dataset2: ChartDataset = {
    id: 'sensor-2',
    label: 'Sensor 2',
    data: [{ x: 0, y: 10 }],
    yAxisId: 'y2', // Second Y-Axis
  };

  const mockPalette: ChartThemePalette = {
    textColor: '#111111',
    gridColor: '#eeeeee',
    seriesColors: ['#0000ff', '#00ff00'],
  };

  describe('Dataset & Color Mapping', () => {
    it('maps chart type and dataset properties correctly', () => {
      const config = buildChartConfig('line', [dataset1], {});

      expect(config.type).toBe('line');
      expect(config.data.datasets).toHaveLength(1);
      expect(config.data.datasets[0]).toMatchObject({
        label: 'Sensor 1',
        data: dataset1.data,
        borderColor: '#ff0000',
        backgroundColor: '#ff0000',
        showLine: true,
        parsing: false,
      });
    });

    it('falls back to theme palette colors when dataset color is undefined', () => {
      const config = buildChartConfig('line', [dataset2], {}, mockPalette);

      expect(config.data.datasets[0].borderColor).toBe('#0000ff');
    });

    it('handles non-array or missing datasets gracefully without throwing', () => {
      // @ts-expect-error testing invalid runtime input
      const config = buildChartConfig('line', null, {});

      expect(config.data.datasets).toEqual([]);
    });
  });

  describe('Scales Configuration', () => {
    it('sets the X-axis scale type based on options or defaults to linear', () => {
      const timeConfig = buildChartConfig('line', [dataset1], { xAxisType: 'time' });
      const linearConfig = buildChartConfig('line', [dataset1], {});

      expect(timeConfig.options?.scales?.['x']).toMatchObject({
        type: 'time',
        time: { tooltipFormat: 'PPpp' },
      });
      expect(linearConfig.options?.scales?.['x']?.type).toBe('linear');
    });

    it('dynamically generates multiple Y-axes with alternating positions', () => {
      const config = buildChartConfig('line', [dataset1, dataset2], {
        yAxisLabels: { y: 'Pressure', y2: 'Stress' },
      });

      const scales = config.options?.scales as Record<string, any>;

      // First axis (y) -> Left side, grid lines drawn
      expect(scales['y']).toMatchObject({
        position: 'left',
        title: { display: true, text: 'Pressure' },
        grid: { drawOnChartArea: true },
      });

      // Second axis (y2) -> Right side, grid lines suppressed to prevent overlap
      expect(scales['y2']).toMatchObject({
        position: 'right',
        title: { display: true, text: 'Stress' },
        grid: { drawOnChartArea: false },
      });
    });
  });

  describe('Theme & Options Integration', () => {
    it('applies title, subtitle, axis labels, and theme colors', () => {
      const config = buildChartConfig(
        'line',
        [dataset1],
        {
          title: 'Experiment Chart',
          subtitle: 'Run 42',
          xAxisLabel: 'Length [cm]',
          showLegend: false,
        },
        mockPalette,
      );

      const plugins = config.options?.plugins as any;
      const scales = config.options?.scales as any;

      expect(plugins.title).toMatchObject({
        display: true,
        text: 'Experiment Chart',
        color: '#111111',
      });
      expect(plugins.subtitle).toMatchObject({
        display: true,
        text: 'Run 42',
        color: '#111111',
      });
      expect(plugins.legend.display).toBe(false);

      expect(scales.x.title).toMatchObject({
        display: true,
        text: 'Length [cm]',
        color: '#111111',
      });
      expect(scales.x.grid.color).toBe('#eeeeee');
    });

    it('enables zoom and pan functionality in plugins', () => {
      const config = buildChartConfig('scatter', [dataset1], {}, mockPalette);

      const zoomPlugin = (config.options?.plugins as any)?.zoom?.zoom;
      expect(zoomPlugin).toBeDefined();
      expect(zoomPlugin.wheel.enabled).toBe(false);
      expect(zoomPlugin.drag.enabled).toBe(true);
      expect(zoomPlugin.pinch.enabled).toBe(false);
    });
  });

  describe('Tooltip Configuration', () => {
    it('does not render a tooltip title', () => {
      const config = buildChartConfig('line', [dataset1], {});

      const titleCallback = (config.options?.plugins as any)?.tooltip?.callbacks?.title;
      expect(titleCallback).toBeUndefined();
    });

    it('renders date, value, y-axis title, empty row, and dataset label in tooltip body', () => {
      const config = buildChartConfig('line', [dataset1], {
        yAxisLabels: { y: 'Pressure' },
      });

      const labelCallback = (config.options?.plugins as any)?.tooltip?.callbacks?.label;
      const labels = labelCallback({
        dataset: { label: 'Sensor 1', yAxisID: 'y' },
        parsed: { x: 0, y: 2.4 },
        chart: {
          scales: {
            y: { options: { title: { text: 'Pressure' } } },
          },
        },
      } as any);

      expect(labels[0]).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/);
      expect(labels.slice(1)).toEqual(['2.4 Pressure', '', 'Sensor 1']);
    });

    it('renders date, value, empty row, and dataset label when no y-axis title is configured', () => {
      const config = buildChartConfig('line', [dataset1], {});

      const labelCallback = (config.options?.plugins as any)?.tooltip?.callbacks?.label;
      const labels = labelCallback({
        dataset: { label: 'Sensor 1', yAxisID: 'y' },
        parsed: { x: 0, y: 2.4 },
        chart: {
          scales: {
            y: { options: { title: { text: '' } } },
          },
        },
      } as any);

      expect(labels[0]).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/);
      expect(labels.slice(1)).toEqual(['2.4 ', '', 'Sensor 1']);
    });
  });
});
