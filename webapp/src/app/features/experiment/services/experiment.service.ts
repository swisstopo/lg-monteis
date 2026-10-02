import { Injectable, inject, resource, signal } from '@angular/core';
import {
  ExperimentControllerService,
  ExperimentResponseDto,
  WriteExperimentDto,
} from '@core/generated';
import { skipGlobalErrorToast } from '@core/http/http-context';
import { toPagedRequestParams } from '@ui/table/paged-request.mapper';
import { IGetRowsParams } from 'ag-grid-community';
import { firstValueFrom } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ExperimentService {
  private readonly api = inject(ExperimentControllerService);
  // Bumped whenever an experiment is created/updated, so the experiment table can refresh its
  // ag-grid infinite row model cache - ag-grid has no way to detect that on its own.
  readonly experimentsChanged = signal(false);

  readonly allExperiments = resource({
    loader: () => firstValueFrom(this.api.getAllExperiments()),
  });

  getExperiment(id: string): Promise<ExperimentResponseDto> {
    return firstValueFrom(this.api.getExperiment(id));
  }

  /** Creates the experiment, or updates it when it already has an id. */
  async saveExperiment(experiment: WriteExperimentDto): Promise<ExperimentResponseDto> {
    const saved = await firstValueFrom(
      experiment.id
        ? this.api.updateExperiment(experiment.id, experiment)
        : this.api.createExperiment(experiment),
    );
    this.experimentsChanged.set(true);
    this.allExperiments.reload();
    return saved;
  }

  getExperiments(params: IGetRowsParams) {
    const { startRow, endRow, sortModel, filterModel } = toPagedRequestParams(params);
    return firstValueFrom(this.api.getExperiments(startRow, endRow, sortModel, filterModel));
  }

  getExperimentsCsv(sortModel?: string, filterModel?: string): Promise<Blob> {
    return firstValueFrom(
      this.api.getExperimentsCsv(sortModel, filterModel, 'body', false, {
        context: skipGlobalErrorToast(),
      }),
    );
  }
}
