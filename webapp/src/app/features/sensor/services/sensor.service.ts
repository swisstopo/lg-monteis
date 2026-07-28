import { Injectable, inject, resource, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { SensorControllerService, WriteSensorDto } from '../../../core/generated';
import { createSavingState } from '../../../shared/utils/saving-state';

@Injectable({ providedIn: 'root' })
export class SensorService {
  private readonly api = inject(SensorControllerService);
  private readonly selectedSensorId = signal<number | undefined>(undefined);
  private readonly savingState = createSavingState();

  readonly saving = this.savingState.saving;
  readonly saveError = this.savingState.error;

  readonly sensor = resource({
    params: () => this.selectedSensorId(),
    loader: ({ params: id }) => {
      if (!id) return Promise.reject(new Error('No sensor id'));
      return this.savingState.run(() => firstValueFrom(this.api.getSensor(id)));
    },
  });

  readonly allFormulas = resource({
    loader: () => firstValueFrom(this.api.findAllFormulas()),
  });

  selectSensor(id: number | undefined) {
    this.selectedSensorId.set(id);
  }

  createSensor(sensor: WriteSensorDto) {
    return this.savingState.run(() => firstValueFrom(this.api.createSensor(sensor)));
  }

  updateSensor(id: number, sensor: WriteSensorDto) {
    return this.savingState.run(() => firstValueFrom(this.api.updateSensor(id, sensor)));
  }
}
