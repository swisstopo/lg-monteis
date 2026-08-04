import { describe, expect, it } from 'vitest';
import { buildChartConfig } from './chart-config.builder';
import { ChartDataset } from './chart.types';

describe('buildChartConfig', () => {
  const dataset: ChartDataset = {
    id: 1,
    label: 'Sensor 1',
    data: [
      { x: 0, y: 1 },
      { x: 1, y: 2 },
    ],
    color: '#ff0000',
  };

  it('maps type, labels and datasets', () => {
    const config = buildChartConfig('line', [dataset], ['a', 'b'], {});

    expect(config.type).toBe('line');
    expect(config.data.labels).toEqual(['a', 'b']);
    expect(config.data.datasets).toHaveLength(1);
    expect(config.data.datasets[0]).toMatchObject({
      label: 'Sensor 1',
      data: dataset.data,
      borderColor: '#ff0000',
    });
  });

  it('uses a linear x scale for scatter charts and category for line charts', () => {
    const scatterConfig = buildChartConfig('scatter', [dataset], [], {});
    const lineConfig = buildChartConfig('line', [dataset], [], {});

    expect(scatterConfig.options?.scales?.['x']).toMatchObject({ type: 'linear' });
    expect(lineConfig.options?.scales?.['x']).toMatchObject({ type: 'category' });
  });

  it('applies axis labels and legend visibility from options', () => {
    const config = buildChartConfig('line', [dataset], [], {
      xAxisLabel: 'Time',
      yAxisLabel: 'Value',
      showLegend: false,
    });

    expect(config.options?.scales?.['x']).toMatchObject({
      title: { display: true, text: 'Time' },
    });
    expect(config.options?.scales?.['y']).toMatchObject({
      title: { display: true, text: 'Value' },
    });
    expect(config.options?.plugins?.legend).toMatchObject({ display: false });
  });
});
