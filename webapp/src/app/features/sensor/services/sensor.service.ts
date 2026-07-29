import { Injectable, inject, resource, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { SensorControllerService, WriteSensorDto } from '../../../core/generated';
import { createSavingState } from '../../../shared/utils/saving-state';

@Injectable({ providedIn: 'root' })
export class SensorService {
  private readonly api = inject(SensorControllerService);
  private readonly sensorRequest = signal<{ id: number | undefined }>({ id: undefined });
  private readonly savingState = createSavingState();

  readonly saving = this.savingState.saving;
  readonly saveError = this.savingState.error;

  readonly sensor = resource({
    params: () => this.sensorRequest(),
    loader: ({ params }) => {
      if (!params.id) return Promise.reject(new Error('No sensor id'));
      return this.savingState.run(() => firstValueFrom(this.api.getSensor(<number>params.id)));
    },
  });

  readonly allFormulas = resource({
    loader: () => firstValueFrom(this.api.findAllFormulas()),
  });

  // always set the sensor id to refetch the sensor
  getSensor(id: number | undefined) {
    if (id === undefined) {
      this.sensor.value.set(undefined);
      return;
    }
    this.sensorRequest.set({ id });
  }

  createSensor(sensor: WriteSensorDto) {
    return this.savingState.run(() => firstValueFrom(this.api.createSensor(sensor)));
  }

  updateSensor(id: number, sensor: WriteSensorDto) {
    return this.savingState.run(() => firstValueFrom(this.api.updateSensor(id, sensor)));
  }
}
