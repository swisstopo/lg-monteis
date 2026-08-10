import { ChartDataset } from './chart.types';

export interface PressurePlateau {
  fromDate: string;
  toDate: string;
  baseValueKpa: number;
  pointCount: number;
  jitterKpa: number;
}

export function generateMockPlateauDataset(
  id: string,
  label: string,
  plateaus: PressurePlateau[],
  color?: string,
  yAxisId?: string,
): ChartDataset {
  const data = plateaus.flatMap(({ fromDate, toDate, baseValueKpa, pointCount, jitterKpa }) => {
    const fromMs = Date.parse(fromDate);
    const toMs = Date.parse(toDate);
    return Array.from({ length: pointCount }, () => ({
      x: fromMs + Math.random() * (toMs - fromMs),
      y: Math.round((baseValueKpa + (Math.random() - 0.5) * 2 * jitterKpa) * 1000) / 1000,
    }));
  });
  data.sort((a, b) => a.x - b.x);

  return { id, label, data, color, yAxisId };
}

export function generateMockPressureDataset(): ChartDataset {
  // Roughly mimics the stepped BSW06_PP1 fluid pressure readings (kPa) over time.
  return generateMockPlateauDataset(
    'BSW06_PP1',
    'BSW06_PP_1 [kPa]',
    [
      {
        fromDate: '2026-05-20',
        toDate: '2026-05-23',
        baseValueKpa: 108.38,
        pointCount: 40,
        jitterKpa: 0.06,
      },
      {
        fromDate: '2026-05-23',
        toDate: '2026-05-31',
        baseValueKpa: 108.48,
        pointCount: 90,
        jitterKpa: 0.015,
      },
      {
        fromDate: '2026-05-31',
        toDate: '2026-06-07',
        baseValueKpa: 108.58,
        pointCount: 90,
        jitterKpa: 0.015,
      },
      {
        fromDate: '2026-06-07',
        toDate: '2026-06-14',
        baseValueKpa: 108.68,
        pointCount: 80,
        jitterKpa: 0.03,
      },
      {
        fromDate: '2026-06-14',
        toDate: '2026-06-21',
        baseValueKpa: 108.77,
        pointCount: 90,
        jitterKpa: 0.03,
      },
      {
        fromDate: '2026-06-21',
        toDate: '2026-06-24',
        baseValueKpa: 108.87,
        pointCount: 25,
        jitterKpa: 0.015,
      },
    ],
    undefined,
    'y',
  );
}

export function generateMockStressRadialDataset(): ChartDataset {
  const fromMs = Date.parse('2026-05-20');
  const toMs = Date.parse('2026-06-24');
  const pointCount = 400;

  const data = Array.from({ length: pointCount }, () => {
    const x = fromMs + Math.random() * (toMs - fromMs);
    const progress = (x - fromMs) / (toMs - fromMs);
    // Smooth dip-and-recover trend plus random noise.
    const trend = 24.2 - 0.02 * Math.sin(progress * Math.PI * 1.3);
    const noise = (Math.random() - 0.5) * 2 * 0.006;
    return { x, y: Math.round((trend + noise) * 1000) / 1000 };
  });
  data.sort((a, b) => a.x - b.x);

  return {
    id: 'BSW22_SR_6',
    label: 'BSW22_SR_6 [bar]',
    data,
    color: '#529c66',
    yAxisId: 'y2',
  };
}

export function generateMockTemperatureDataset(): ChartDataset {
  const fromMs = Date.parse('2026-05-20');
  const toMs = Date.parse('2026-06-24');
  const pointCount = 300;
  const dayMs = 24 * 60 * 60 * 1000;

  const data = Array.from({ length: pointCount }, () => {
    const x = fromMs + Math.random() * (toMs - fromMs);
    // Simulate day/night cycles using a sine wave based on elapsed days
    const daysElapsed = (x - fromMs) / dayMs;
    const trend = 14 + 3 * Math.sin(daysElapsed * Math.PI * 2);
    const noise = (Math.random() - 0.5) * 1.5;

    return { x, y: Math.round((trend + noise) * 10) / 10 };
  });

  data.sort((a, b) => a.x - b.x);

  return {
    id: 'BSW_TEMP_1',
    label: 'Temperature [°C]',
    data,
    yAxisId: 'y3',
  };
}

export function generateMockHumidityDataset(): ChartDataset {
  const fromMs = Date.parse('2026-05-20');
  const toMs = Date.parse('2026-06-24');
  const pointCount = 250;

  const data = Array.from({ length: pointCount }, () => {
    const x = fromMs + Math.random() * (toMs - fromMs);
    const progress = (x - fromMs) / (toMs - fromMs);

    // Simulate a slow drying trend over the month
    const trend = 55 - 10 * progress;
    const noise = (Math.random() - 0.5) * 6;

    return { x, y: Math.round((trend + noise) * 10) / 10 };
  });

  data.sort((a, b) => a.x - b.x);

  return {
    id: 'BSW_HUM_1',
    label: 'Relative Humidity [%]',
    data,
    yAxisId: 'y4',
  };
}

export function generateTaupeDataset(): ChartDataset {
  return {
    id: 'taupe_sensor_1',
    label: 'Taupe Sensor 1',
    data: [
      { x: 10, y: 2.4 },
      { x: 25, y: 2.8 },
      { x: 50, y: 3.1 },
      { x: 120, y: 4.5 },
    ],
    yAxisId: 'y',
  };
}
