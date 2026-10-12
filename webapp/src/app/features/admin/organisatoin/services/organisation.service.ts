import { Injectable, computed, inject, resource, signal } from '@angular/core';
import {
  OrganisationControllerService,
  OrganisationResponseDto,
  WriteOrganisationDto,
} from '@core/generated';
import { skipGlobalErrorToast } from '@core/http/http-context';
import { toPagedRequestParams } from '@ui/table/paged-request.mapper';
import { IGetRowsParams } from 'ag-grid-community';
import { firstValueFrom } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class OrganisationService {
  private readonly api = inject(OrganisationControllerService);
  private readonly saveCount = signal(0);
  /**
   * Grows with every saved organisation. The organisation table refreshes its rows on each change,
   * its ag-grid infinite row model cannot notice a save on its own. A counter, not a flag: the
   * table only reads it, nothing has to reset it.
   */
  readonly organisationsSaved = this.saveCount.asReadonly();

  private readonly loadedOrganisations = resource({
    loader: () => firstValueFrom(this.api.getAllOrganisations()),
  });
  // empty while loading and after a failed load, the resource's value() throws in that case
  readonly allOrganisations = computed(() =>
    this.loadedOrganisations.hasValue() ? this.loadedOrganisations.value() : [],
  );

  getOrganisation(id: string): Promise<OrganisationResponseDto> {
    return firstValueFrom(this.api.getOrganisation(id));
  }

  /** Creates the organisation, or updates the one with `id`. */
  async saveOrganisation(
    id: string | undefined,
    organisation: WriteOrganisationDto,
  ): Promise<OrganisationResponseDto> {
    const saved = await firstValueFrom(
      id
        ? this.api.updateOrganisation(id, organisation)
        : this.api.createOrganisation(organisation),
    );
    this.saveCount.update((count) => count + 1);
    this.loadedOrganisations.reload();
    return saved;
  }

  getOrganisations(params: IGetRowsParams) {
    const { startRow, endRow, sortModel, filterModel } = toPagedRequestParams(params);
    return firstValueFrom(this.api.getOrganisations(startRow, endRow, sortModel, filterModel));
  }

  getOrganisationsCsv(sortModel?: string, filterModel?: string): Promise<Blob> {
    return firstValueFrom(
      this.api.getOrganisationsCsv(sortModel, filterModel, 'body', false, {
        context: skipGlobalErrorToast(),
      }),
    );
  }
}
