import { Injectable, inject, resource, signal } from '@angular/core';
import { translate } from '@ngx-translate/core';
import { IGetRowsParams } from 'ag-grid-community';
import { firstValueFrom } from 'rxjs';
import { ErrorDto, ExperimentControllerService, WriteExperimentDto } from '../../../core/generated';
import { toErrorDtos } from '../../../core/http/api-error.model';
import { toPagedRequestParams } from '../../../ui/table/paged-request.mapper';

@Injectable({ providedIn: 'root' })
export class ExperimentService {
  private readonly api = inject(ExperimentControllerService);
  private readonly experimentRequest = signal<{ id: string | undefined }>({ id: undefined });
  readonly error = signal<ErrorDto[] | undefined>(undefined);
  // Bumped whenever a sensor is created/updated, so the sensor table can refresh its
  // ag-grid infinite row model cache - ag-grid has no way to detect that on its own.
  readonly experimentsChanged = signal(false);

  readonly experiment = resource({
    params: () => this.experimentRequest(),
    loader: ({ params }) => {
      if (!params.id) return Promise.reject(new Error(translate('experiment.error.noId')()));
      return firstValueFrom(this.api.getExperiment(params.id));
    },
  });

  // always set the experiment id to refetch the experiment
  getExperiment(id: string | undefined) {
    if (id === undefined) {
      this.experiment.value.set(undefined);
      return;
    }
    this.experimentRequest.set({ id });
  }

  async createExperiment(experiment: WriteExperimentDto) {
    try {
      const result = await firstValueFrom(this.api.createExperiment(experiment));
      this.experimentsChanged.set(true);
      return result;
    } catch (err) {
      this.error.set(toErrorDtos(err));
      throw err;
    }
  }

  async updateExperiment(id: string, experiment: WriteExperimentDto) {
    try {
      const result = await firstValueFrom(this.api.updateExperiment(id, experiment));
      this.experimentsChanged.set(true);
      return result;
    } catch (err) {
      this.error.set(toErrorDtos(err));
      throw err;
    }
  }

  getExperiments(params: IGetRowsParams) {
    const { startRow, endRow, sortModel, filterModel } = toPagedRequestParams(params);
    return firstValueFrom(this.api.getExperiments(startRow, endRow, sortModel, filterModel));
  }
}
