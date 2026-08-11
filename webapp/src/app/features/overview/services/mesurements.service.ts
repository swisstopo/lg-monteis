import { inject, Injectable, resource, signal } from '@angular/core';
import { translate } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { ChartDataResponseDto, MeasurementControllerService } from '../../../core/generated';
import { ChartDataset, ChartPoint } from '../../../ui/chart';

interface ChartRequest {
  ids: number[];
  rangeFrom: string;
  rangeTo: string;
}

interface LoadedChartData {
  datasets: ChartDataset[];
  yAxisLabels: Record<string, string>;
}

@Injectable({ providedIn: 'root' })
export class MesurementsService {
  private readonly api = inject(MeasurementControllerService);
  private readonly chartsRequest = signal<ChartRequest | undefined>(undefined);

  readonly chartData = resource<LoadedChartData, ChartRequest | undefined>({
    params: () => this.chartsRequest(),
    loader: async ({ params }) => {
      if (!params?.ids.length) throw new Error(translate('chart.error.noIds')());

      const rawData = await firstValueFrom(
        this.api.getChartsData(params.ids, params.rangeFrom, params.rangeTo),
      );

      return {
        datasets: this.mapApiToChartDatasets(rawData),
        yAxisLabels: this.buildDynamicYAxisLabels(rawData),
      };
    },
  });

  getChartData(ids: number[], rangeFrom: string, rangeTo: string) {
    if (!ids.length) {
      this.chartsRequest.set(undefined);
      return;
    }
    this.chartsRequest.set({ ids: ids, rangeFrom: rangeFrom, rangeTo: rangeTo });
  }

  mapApiToChartDatasets(apiResponses: ChartDataResponseDto[]): ChartDataset[] {
    const axisIdsByUnit = this.buildAxisIdsByUnit(apiResponses);

    return apiResponses.map((sensor, index) => {
      const data: ChartPoint[] = (sensor.data ?? []).map((item) => ({
        x: Date.parse(item.timestamp!),
        y: item.value!,
      }));

      return {
        id: sensor.id ?? `sensor-${index}`,
        label: `${sensor.sensorName ?? 'Unknown'} (${sensor.sensorCode ?? ''}) [${sensor.unit ?? ''}]`,
        data: data,
        yAxisId: axisIdsByUnit.get(sensor.unit ?? ''),
      };
    });
  }

  buildDynamicYAxisLabels(apiResponses: ChartDataResponseDto[]): Record<string, string> {
    const axisIdsByUnit = this.buildAxisIdsByUnit(apiResponses);
    const labels: Record<string, string> = {};

    axisIdsByUnit.forEach((axisId, unit) => {
      labels[axisId] = unit ? `${unit}` : '';
    });

    return labels;
  }

  /** Groups sensors by unit so datasets sharing the same unit reuse a single Y axis. */
  private buildAxisIdsByUnit(apiResponses: ChartDataResponseDto[]): Map<string, string> {
    const axisIdsByUnit = new Map<string, string>();

    apiResponses.forEach((sensor) => {
      const unit = sensor.unit ?? '';
      if (axisIdsByUnit.has(unit)) return;

      const axisId = axisIdsByUnit.size === 0 ? 'y' : `y${axisIdsByUnit.size + 1}`;
      axisIdsByUnit.set(unit, axisId);
    });

    return axisIdsByUnit;
  }
}
