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
      if (!params?.ids.length) return Promise.reject(new Error(translate('chart.error.noIds')()));

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
    return apiResponses.map((sensor, index) => {
      const data: ChartPoint[] = (sensor.data ?? []).map((item) => ({
        x: Date.parse(item.timestamp!),
        y: item.value!,
      }));

      return {
        id: sensor.id ?? `sensor-${index}`,
        label: `${sensor.sensorName ?? 'Unknown'} (${sensor.sensorCode ?? ''}) [${sensor.unit ?? ''}]`,
        data: data,
        yAxisId: this.getAxisId(index),
      };
    });
  }

  getAxisId(index: number): string {
    return index === 0 ? 'y' : `y${index + 1}`;
  }

  buildDynamicYAxisLabels(apiResponses: ChartDataResponseDto[]): Record<string, string> {
    const labels: Record<string, string> = {};

    apiResponses.forEach((sensor, index) => {
      const axisId = this.getAxisId(index);
      const sensorName = sensor.sensorName ?? sensor.sensorCode ?? `Sensor ${index + 1}`;
      const unit = sensor.unit ? ` ${sensor.unit}` : '';

      labels[axisId] = `${unit}`;
    });

    return labels;
  }
}
