import { computed, inject, Injectable, resource, signal } from '@angular/core';
import { translate, TranslateService } from '@ngx-translate/core';
import { firstValueFrom, fromEvent, takeUntil } from 'rxjs';
import {
  ChartDataResponseDto,
  ErrorDto,
  MeasurementControllerService,
} from '../../../core/generated';
import { toErrorDtos } from '../../../core/http/api-error.model';
import { ChartDataset, ChartPoint } from '../../../ui/chart';

interface ChartRequest {
  ids: string[];
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
  private readonly translateService = inject(TranslateService);
  private readonly chartsRequest = signal<ChartRequest | undefined>(undefined);
  readonly error = computed<ErrorDto[] | undefined>(() => {
    const err = this.chartData.error();
    return err ? toErrorDtos(err) : undefined;
  });

  readonly chartData = resource<LoadedChartData, ChartRequest | undefined>({
    params: () => this.chartsRequest(),
    loader: async ({ params, abortSignal }) => {
      if (!params?.ids.length) throw new Error(translate('chart.error.noIds')());

      // one call per sensor, parallelized
      const responses = await Promise.all(
        params.ids.map((id) =>
          firstValueFrom(
            this.api
              .getChartData(id, params.rangeFrom, params.rangeTo)
              .pipe(takeUntil(fromEvent(abortSignal, 'abort'))),
          ),
        ),
      );
      // each response is ChartDataResponseDto[], so flatten them
      const allSensors = responses.flat();

      const axisIdsByUnit = this.buildAxisIdsByUnit(allSensors);
      const datasets = this.mapDtoToChartDatasets(allSensors, axisIdsByUnit);

      return {
        count: datasets.reduce((sum, d) => sum + d.data.length, 0),
        datasets,
        yAxisLabels: this.buildYAxisLabels(axisIdsByUnit),
      };
    },
  });

  getChartData(ids: string[], rangeFrom: string, rangeTo: string) {
    if (!ids.length) {
      this.chartsRequest.set(undefined);
      return;
    }
    this.chartsRequest.set({ ids: ids, rangeFrom: rangeFrom, rangeTo: rangeTo });
  }

  private mapDtoToChartDatasets(
    apiResponses: ChartDataResponseDto[],
    axisIdsByUnit: Map<string, string>,
  ): ChartDataset[] {
    return apiResponses.map((sensor, index) => {
      // use flatMap to safely filter out bad points without crashing the whole chart
      const data: ChartPoint[] = (sensor.data ?? []).flatMap((item) => {
        if (item.timestamp === undefined || item.value === undefined) {
          console.warn(`[Telemetry] Dropped malformed point for sensor ${sensor.sensorCode}`);
          return [];
        }
        return [{ x: Date.parse(item.timestamp), y: item.value }];
      });

      return {
        id: sensor.id ?? `sensor-${index}`,
        label: `${sensor.sensorCode ?? 'unknown'} [${sensor.unit ?? ''}]`,
        data: data,
        yAxisId: axisIdsByUnit.get(sensor.unit ?? ''),
      };
    });
  }

  private buildYAxisLabels(axisIdsByUnit: Map<string, string>): Record<string, string> {
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
