import { signal } from '@angular/core';
import { ErrorDto } from '../../core/generated';
import { toErrorDtos } from '../models/api-error.model';

/**
 * Reusable saving/error state for services performing API mutations
 * (create/update/delete). Wrap the async call with `run` to get consistent
 * `saving` and `error` signals across all feature services, without
 * duplicating try/catch/finally boilerplate.
 *
 * @example
 * export class SensorService {
 *   private readonly savingState = createSavingState();
 *   readonly saving = this.savingState.saving;
 *   readonly saveError = this.savingState.error;
 *
 *   createSensor(sensor: WriteSensorDto) {
 *     return this.savingState.run(() => firstValueFrom(this.api.createSensor(sensor)));
 *   }
 * }
 */ // TODO make generic api state not only saving
export function createSavingState() {
  const saving = signal(false);
  const error = signal<ErrorDto[] | undefined>(undefined);

  async function run<T>(action: () => Promise<T>): Promise<T> {
    saving.set(true);
    error.set(undefined);
    try {
      return await action();
    } catch (err) {
      error.set(toErrorDtos(err));
      throw err;
    } finally {
      saving.set(false);
    }
  }

  return {
    saving: saving.asReadonly(),
    error: error.asReadonly(),
    run,
  };
}
