import { inject, Injectable, resource, signal } from '@angular/core';
import { translate, TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import {
  ChartDataResponseDto,
  ErrorDto,
  MeasurementControllerService,
} from '../../../core/generated';
import { toErrorDtos } from '../../../core/http/api-error.model';
import { ChartDataset, ChartPoint } from '../../../ui/chart';

interface ChartRequest {
  ids: number[];
  rangeFrom: string;
  rangeTo: string;
}

interface LoadedChartData {
  count: number;
  datasets: ChartDataset[];
  yAxisLabels: Record<string, string>;
}

@Injectable({ providedIn: 'root' })
export class MesurementsService {
  private readonly api = inject(MeasurementControllerService);
  private readonly i18nService = inject(TranslateService);
  private readonly chartsRequest = signal<ChartRequest | undefined>(undefined);
  readonly error = signal<ErrorDto[] | undefined>(undefined);

  readonly chartData = resource<LoadedChartData, ChartRequest | undefined>({
    params: () => this.chartsRequest(),
    loader: async ({ params }) => {
      try {
        if (!params?.ids.length) throw new Error(translate('chart.error.noIds')());

        // one call per sensor, parallelized
        const responses = await Promise.all(
          params.ids.map((id) =>
            firstValueFrom(this.api.getChartsData([id], params.rangeFrom, params.rangeTo)),
          ),
        );
        // each response is ChartDataResponseDto[], so flatten them
        const allSensors = responses.flat();

        return {
          count: allSensors.reduce((sum, sensor) => sum + (sensor.data?.length ?? 0), 0),
          datasets: this.mapDtoToChartDatasets(allSensors),
          yAxisLabels: this.buildDynamicYAxisLabels(allSensors),
        };
      } catch (error) {
        this.error.set(toErrorDtos(error));
        throw error;
      }
    },
  });

  getChartData(ids: number[], rangeFrom: string, rangeTo: string) {
    if (!ids.length) {
      this.chartsRequest.set(undefined);
      return;
    }
    this.chartsRequest.set({ ids: ids, rangeFrom: rangeFrom, rangeTo: rangeTo });
  }

  private mapDtoToChartDatasets(apiResponses: ChartDataResponseDto[]): ChartDataset[] {
    const axisIdsByUnit = this.buildAxisIdsByUnit(apiResponses);

    return apiResponses.map((sensor, index) => {
      const data: ChartPoint[] = (sensor.data ?? []).map((item) => {
        if (item.timestamp === undefined || item.value === undefined) {
          throw new Error(this.i18nService.translate('chart.error.invalidData')());
        }
        return { x: Date.parse(item.timestamp!), y: item.value! };
      });

      return {
        id: sensor.id ?? `sensor-${index}`,
        label: `${sensor.sensorName ?? 'Unknown'} (${sensor.sensorCode ?? ''}) [${sensor.unit ?? ''}]`,
        data: data,
        yAxisId: axisIdsByUnit.get(sensor.unit ?? ''),
      };
    });
  }

  private buildDynamicYAxisLabels(apiResponses: ChartDataResponseDto[]): Record<string, string> {
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
