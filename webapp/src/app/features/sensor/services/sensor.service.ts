import { Injectable, inject, resource, signal } from '@angular/core';
import { translate } from '@ngx-translate/core';
import { IGetRowsParams } from 'ag-grid-community';
import { firstValueFrom } from 'rxjs';
import { ErrorDto, SensorControllerService, WriteSensorDto } from '../../../core/generated';
import { toErrorDtos } from '../../../shared/models/api-error.model';
import { toPagedRequestParams } from '../../../ui/table/paged-request.mapper';

@Injectable({ providedIn: 'root' })
export class SensorService {
  private readonly api = inject(SensorControllerService);
  private readonly sensorRequest = signal<{ id: number | undefined }>({ id: undefined });
  readonly error = signal<ErrorDto[] | undefined>(undefined);
  // Bumped whenever a sensor is created/updated, so the sensor table can refresh its
  // ag-grid infinite row model cache - ag-grid has no way to detect that on its own.
  readonly sensorsChanged = signal(0);

  readonly sensor = resource({
    params: () => this.sensorRequest(),
    loader: ({ params }) => {
      if (!params.id) return Promise.reject(new Error(translate('sensor.error.noId')()));
      return firstValueFrom(this.api.getSensor(params.id));
    },
  });

  readonly allFormulas = resource({
    loader: () => firstValueFrom(this.api.findAllFormulas()),
  });

  readonly allTypes = resource({
    loader: () => firstValueFrom(this.api.findAllTypes()),
  });

  // always set the sensor id to refetch the sensor
  getSensor(id: number | undefined) {
    if (id === undefined) {
      this.sensor.value.set(undefined);
      return;
    }
    this.sensorRequest.set({ id });
  }

  async createSensor(sensor: WriteSensorDto) {
    try {
      const result = await firstValueFrom(this.api.createSensor(sensor));
      this.sensorsChanged.update((v) => v + 1);
      return result;
    } catch (err) {
      this.error.set(toErrorDtos(err));
      throw err;
    }
  }

  async updateSensor(id: number, sensor: WriteSensorDto) {
    try {
      const result = await firstValueFrom(this.api.updateSensor(id, sensor));
      this.sensorsChanged.update((v) => v + 1);
      return result;
    } catch (err) {
      this.error.set(toErrorDtos(err));
      throw err;
    }
  }

  getSensors(params: IGetRowsParams) {
    const { startRow, endRow, sortModel, filterModel } = toPagedRequestParams(params);
    return firstValueFrom(this.api.getSensors(startRow, endRow, sortModel, filterModel));
  }
}
