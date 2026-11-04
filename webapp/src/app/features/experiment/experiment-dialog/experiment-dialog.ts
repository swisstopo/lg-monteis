import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  linkedSignal,
  resource,
  signal,
} from '@angular/core';
import {
  apply,
  disabled,
  form,
  FormField,
  FormRoot,
  submit,
  TreeValidationResult,
} from '@angular/forms/signals';
import { MatOption, provideNativeDateAdapter } from '@angular/material/core';
import {
  MatDatepicker,
  MatDatepickerInput,
  MatDatepickerToggle,
} from '@angular/material/datepicker';
import { MatDialogRef } from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import {
  MatError,
  MatFormField,
  MatHint,
  MatInput,
  MatLabel,
  MatPrefix,
  MatSuffix,
} from '@angular/material/input';
import { MatSelect } from '@angular/material/select';
import { PermissionsService } from '@core/auth/permissions.service';
import { ErrorDto, ExperimentResponseDto } from '@core/generated';
import { toErrorDtos } from '@core/http/api-error.model';
import { ToastService } from '@core/notifications/toast.service';
import { FormErrorService } from '@core/utils/form-error.service';
import { OrganisationService } from '@features/admin/organisatoin/services/organisation.service';
import { ExperimentOwnerList } from '@features/experiment/owners/experiment-owner-list';
import { ExperimentOwnerPicker } from '@features/experiment/owners/experiment-owner-picker';
import { ownerIdsOf, sameIds } from '@features/experiment/owners/owner-ids';
import { ExperimentDocumentSource } from '@features/experiment/services/experiment-document.source';
import { ExperimentService } from '@features/experiment/services/experiment.service';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { DialogLayout, DialogMode } from '@ui/dialog/dialog-layout';
import { SubmitButton } from '@ui/dialog/submit-button';
import { DismissButton } from '@ui/dismiss-button/dismiss-button';
import { DocumentSource } from '@ui/documents/document-source';
import { Documents } from '@ui/documents/documents';
import { FieldErrors } from '@ui/field-errors/field-errors';
import { experimentSchema, toFormModel, toWriteDto } from './experiment-form';

const OWNER_IDS_FIELD = 'ownerIds';

@Component({
  selector: 'app-experiment-dialog',
  providers: [
    provideNativeDateAdapter(),
    { provide: DocumentSource, useExisting: ExperimentDocumentSource },
  ],
  imports: [
    DialogLayout,
    SubmitButton,
    DismissButton,
    MatFormField,
    MatLabel,
    MatInput,
    MatError,
    MatHint,
    MatIcon,
    MatPrefix,
    MatSuffix,
    MatSelect,
    MatOption,
    MatDatepicker,
    MatDatepickerInput,
    MatDatepickerToggle,
    FormField,
    FormRoot,
    TranslatePipe,
    Documents,
    ExperimentOwnerList,
    ExperimentOwnerPicker,
    FieldErrors,
  ],
  templateUrl: './experiment-dialog.html',
  styleUrl: './experiment-dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExperimentDialog {
  private readonly experimentService = inject(ExperimentService);
  private readonly organisationService = inject(OrganisationService);
  private readonly permissions = inject(PermissionsService);
  private readonly formErrorService = inject(FormErrorService);
  private readonly toastService = inject(ToastService);
  private readonly translateService = inject(TranslateService);
  private readonly dialogRef = inject<MatDialogRef<ExperimentDialog>>(MatDialogRef);

  protected readonly organisations = this.organisationService.allOrganisations;

  readonly experimentId = input<string>();
  readonly viewOnly = input(false);

  // without write access it is view whatever the caller asked for, the backend would reject a save
  protected readonly mode = computed<DialogMode>(() => {
    const experimentId = this.experimentId();
    if (experimentId === undefined) return 'create';
    if (this.viewOnly()) return 'view';
    return this.permissions.canWriteExperiment(experimentId) ? 'edit' : 'view';
  });
  protected readonly readOnly = computed(() => this.mode() === 'view');

  // no error handling here, the restErrorInterceptor already toasts a failed load
  private readonly loadedExperiment = resource({
    params: () => this.experimentId(),
    loader: ({ params: experimentId }) => this.experimentService.getExperiment(experimentId),
  });

  // undefined again once "save and create new" starts the next experiment
  private readonly experiment = linkedSignal(() =>
    this.loadedExperiment.hasValue() ? this.loadedExperiment.value() : undefined,
  );

  private readonly formModel = linkedSignal(() => toFormModel(this.experiment()));

  protected readonly owners = computed(() => this.experiment()?.owners ?? []);
  protected readonly ownersStatus = computed(() => this.experiment()?.ownersStatus);
  // owners are picked from the PIs, and a new experiment gets its PI group in Keycloak only later.
  // hidden owners can't be edited, the picker would start empty and the save replace them unseen
  protected readonly canManageOwners = computed(
    () =>
      this.mode() === 'edit' &&
      this.permissions.isAdmin() &&
      this.ownersStatus() === ExperimentResponseDto.OwnersStatusEnum.Shown,
  );
  // compared by content: a saved experiment with the same owners must not reset the selection
  private readonly storedOwnerIds = computed(() => ownerIdsOf(this.owners()), {
    equal: sameIds,
  });
  protected readonly ownerIds = linkedSignal(() => this.storedOwnerIds());
  // undefined leaves the owners alone, only a changed selection needs Keycloak
  private readonly changedOwnerIds = computed(() =>
    this.canManageOwners() && !sameIds(this.storedOwnerIds(), this.ownerIds())
      ? this.ownerIds()
      : undefined,
  );
  protected readonly ownerError = signal<string | undefined>(undefined);

  protected readonly experimentForm = form(
    this.formModel,
    (path) => {
      apply(path, experimentSchema(this.translateService));
      disabled(path, this.readOnly);
    },
    { submission: { action: () => this.save(() => this.dialogRef.close()) } },
  );

  protected saveAndCreateNew(): Promise<boolean> {
    return submit(this.experimentForm, () => this.save(() => this.startNextExperiment()));
  }

  private async save(afterSave: () => void): Promise<TreeValidationResult> {
    const ownerIds = this.changedOwnerIds();
    this.ownerError.set(undefined);
    try {
      const saved = await this.experimentService.saveExperiment(
        toWriteDto(this.formModel(), this.experiment(), ownerIds),
      );
      this.toastSaved(saved, ownerIds);
    } catch (error) {
      return this.showSaveErrors(toErrorDtos(error));
    }
    afterSave();
    return undefined;
  }

  private toastSaved(saved: ExperimentResponseDto, ownerIds: string[] | undefined): void {
    if (ownerIds && !ownersSaved(saved, ownerIds)) {
      this.toastService.warning(this.translateService.translate('experiment.owners.notSaved')());
    } else {
      this.toastService.success(this.translateService.translate('experiment.success')());
    }
  }

  private showSaveErrors(errors: ErrorDto[]): TreeValidationResult {
    this.ownerError.set(errors.find((e) => e.field === OWNER_IDS_FIELD)?.messageKey);
    return this.formErrorService.mapApiErrorsToFormErrors(
      errors.filter((e) => e.field !== OWNER_IDS_FIELD),
      this.experimentForm,
      'experiment.error.unspecified.message',
    );
  }

  // the form model is set as well: in create mode experiment already is undefined, setting it
  // again would not recompute the form model
  private startNextExperiment(): void {
    this.experiment.set(undefined);
    this.ownerError.set(undefined);
    this.formModel.set(toFormModel(undefined));
    this.experimentForm().reset();
  }
}

// the backend keeps the stored owners when Keycloak fails and still saves everything else
function ownersSaved(saved: ExperimentResponseDto, ownerIds: string[]): boolean {
  return (
    saved.ownersStatus === ExperimentResponseDto.OwnersStatusEnum.Shown &&
    sameIds(ownerIdsOf(saved.owners), ownerIds)
  );
}
