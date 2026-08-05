import {
  CategoryScale,
  Chart,
  Legend,
  LinearScale,
  LineController,
  LineElement,
  PointElement,
  ScatterController,
  SubTitle,
  Title,
  Tooltip,
} from 'chart.js';

let registered = false;

export function registerChartJs(): void {
  if (registered) {
    return;
  }
  Chart.register(
    LineController,
    ScatterController,
    LinearScale,
    CategoryScale,
    PointElement,
    LineElement,
    Tooltip,
    Legend,
    Title,
    SubTitle,
  );
  registered = true;
}
