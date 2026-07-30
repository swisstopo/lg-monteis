import { Injectable, inject, resource, signal } from '@angular/core';
import { translate } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { ErrorDto, SensorControllerService, WriteSensorDto } from '../../../core/generated';
import { toErrorDtos } from '../../../shared/models/api-error.model';

@Injectable({ providedIn: 'root' })
export class SensorService {
  private readonly api = inject(SensorControllerService);
  private readonly sensorRequest = signal<{ id: number | undefined }>({ id: undefined });
  readonly error = signal<ErrorDto[] | undefined>(undefined);

  readonly sensor = resource({
    params: () => this.sensorRequest(),
    loader: ({ params }) => {
      if (!params.id) return Promise.reject(new Error(translate('sensor.edit.error.noId')()));
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
      return await firstValueFrom(this.api.createSensor(sensor));
    } catch (err) {
      this.error.set(toErrorDtos(err));
      throw err;
    }
  }

  async updateSensor(id: number, sensor: WriteSensorDto) {
    try {
      return await firstValueFrom(this.api.updateSensor(id, sensor));
    } catch (err) {
      this.error.set(toErrorDtos(err));
      throw err;
    }
  }
}
