import { Injectable, inject, resource, signal } from '@angular/core';
import { ErrorDto, OrganisationControllerService, WriteOrganisationDto } from '@core/generated';
import { toErrorDtos } from '@core/http/api-error.model';
import { skipGlobalErrorToast } from '@core/http/http-context';
import { translate } from '@ngx-translate/core';
import { toPagedRequestParams } from '@ui/table/paged-request.mapper';
import { IGetRowsParams } from 'ag-grid-community';
import { firstValueFrom } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class OrganisationService {
  private readonly api = inject(OrganisationControllerService);
  private readonly organisationRequest = signal<{ id: string | undefined }>({ id: undefined });
  readonly error = signal<ErrorDto[] | undefined>(undefined);
  // Bumped whenever an organisation is created/updated, so the organisation table can refresh its
  // ag-grid infinite row model cache - ag-grid has no way to detect that on its own.
  readonly organisationsChanged = signal(false);

  readonly organisation = resource({
    params: () => this.organisationRequest(),
    loader: ({ params }) => {
      if (!params.id) return Promise.reject(new Error(translate('organisation.error.noId')()));
      return firstValueFrom(this.api.getOrganisation(params.id));
    },
  });

  readonly allOrganisations = resource({
    loader: () => firstValueFrom(this.api.getAllOrganisations()),
  });

  // always set the organisation id to refetch the organisation
  getOrganisation(id: string | undefined) {
    if (id === undefined) {
      this.organisation.value.set(undefined);
      return;
    }
    this.organisationRequest.set({ id });
  }

  async createOrganisation(organisation: WriteOrganisationDto) {
    try {
      const result = await firstValueFrom(this.api.createOrganisation(organisation));
      this.organisationsChanged.set(true);
      this.allOrganisations.reload();
      return result;
    } catch (err) {
      this.error.set(toErrorDtos(err));
      throw err;
    }
  }

  async updateOrganisation(id: string, organisation: WriteOrganisationDto) {
    try {
      const result = await firstValueFrom(this.api.updateOrganisation(id, organisation));
      this.organisationsChanged.set(true);
      this.allOrganisations.reload();
      return result;
    } catch (err) {
      this.error.set(toErrorDtos(err));
      throw err;
    }
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
