import { Injectable, inject, resource, signal } from '@angular/core';
import { translate } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { ErrorDto, ExperimentControllerService, WriteExperimentDto } from '../../../core/generated';
import { toErrorDtos } from '../../../shared/models/api-error.model';

@Injectable({ providedIn: 'root' })
export class ExperimentService {
  private readonly api = inject(ExperimentControllerService);
  private readonly experimentRequest = signal<{ id: number | undefined }>({ id: undefined });
  readonly error = signal<ErrorDto[] | undefined>(undefined);

  readonly experiment = resource({
    params: () => this.experimentRequest(),
    loader: ({ params }) => {
      if (!params.id) return Promise.reject(new Error(translate('experiment.error.noId')()));
      return firstValueFrom(this.api.getExperiment(params.id));
    },
  });

  // always set the experiment id to refetch the experiment
  getExperiment(id: number | undefined) {
    if (id === undefined) {
      this.experiment.value.set(undefined);
      return;
    }
    this.experimentRequest.set({ id });
  }

  async createExperiment(experiment: WriteExperimentDto) {
    try {
      return await firstValueFrom(this.api.createExperiment(experiment));
    } catch (err) {
      this.error.set(toErrorDtos(err));
      throw err;
    }
  }

  async updateExperiment(id: number, experiment: WriteExperimentDto) {
    try {
      return await firstValueFrom(this.api.updateExperiment(id, experiment));
    } catch (err) {
      this.error.set(toErrorDtos(err));
      throw err;
    }
  }
}
