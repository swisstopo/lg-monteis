import { Injectable, inject, resource, signal } from '@angular/core';
import {
  ExperimentControllerService,
  ExperimentResponseDto,
  WriteExperimentDto,
} from '@core/generated';
import {
  ErrorDto,
  ExperimentControllerService,
  ExperimentOwnerControllerService,
  WriteExperimentDto,
} from '@core/generated';
import { toErrorDtos } from '@core/http/api-error.model';
import { skipGlobalErrorToast } from '@core/http/http-context';
import { toPagedRequestParams } from '@ui/table/paged-request.mapper';
import { IGetRowsParams } from 'ag-grid-community';
import { firstValueFrom } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ExperimentService {
  private readonly api = inject(ExperimentControllerService);
  private readonly ownerApi = inject(ExperimentOwnerControllerService);
  private readonly saveCount = signal(0);
  /**
   * Grows with every saved experiment. The experiment table refreshes its rows on each change, its
   * ag-grid infinite row model cannot notice a save on its own. A counter, not a flag: the table
   * only reads it, nothing has to reset it.
   */
  readonly experimentsSaved = this.saveCount.asReadonly();

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
    this.saveCount.update((count) => count + 1);
    this.allExperiments.reload();
    return saved;
  }

  async replaceOwners(id: string, ownerIds: string[]) {
    try {
      const result = await firstValueFrom(this.ownerApi.replaceOwners(id, { ownerIds }));
      this.experimentsChanged.set(true);
      return result;
    } catch (err) {
      this.error.set(toErrorDtos(err));
      throw err;
    }
  }

  getOwnerCandidates(id: string) {
    // the owner picker shows the failure itself
    return firstValueFrom(
      this.ownerApi.getOwnerCandidates(id, 'body', false, { context: skipGlobalErrorToast() }),
    );
  }

  getAssignedOwners() {
    return firstValueFrom(this.ownerApi.getAssignedOwners());
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
