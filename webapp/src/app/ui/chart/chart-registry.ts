import {
  Chart,
  Legend,
  LinearScale,
  LineController,
  LineElement,
  PointElement,
  ScatterController,
  SubTitle,
  TimeScale,
  Title,
  Tooltip,
} from 'chart.js';
import 'chartjs-adapter-date-fns';

let registered = false;

/**
 * Register only the necessary chart.js components for tree shaking the chart.js library.
 */
export function registerChartJs(): void {
  if (registered) {
    return;
  }
  Chart.register(
    LineController,
    ScatterController,
    LinearScale,
    PointElement,
    LineElement,
    Tooltip,
    Legend,
    TimeScale,
    Title,
    SubTitle,
  );
  registered = true;
}
