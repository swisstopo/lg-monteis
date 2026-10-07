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
import { provideNativeDateAdapter } from '@angular/material/core';
import {
  MatDatepicker,
  MatDatepickerInput,
  MatDatepickerToggle,
} from '@angular/material/datepicker';
import { MatDialogRef } from '@angular/material/dialog';
import { MatError, MatFormField, MatInput, MatLabel, MatSuffix } from '@angular/material/input';
import { PermissionsService } from '@core/auth/permissions.service';
import { toErrorDtos } from '@core/http/api-error.model';
import { ToastService } from '@core/notifications/toast.service';
import { FormErrorService } from '@core/utils/form-error.service';
import { ExperimentDocuments } from '@features/experiment/experiment-documents/experiment-documents';
import { ExperimentOwnerList } from '@features/experiment/owners/experiment-owner-list';
import { ExperimentOwnerPicker } from '@features/experiment/owners/experiment-owner-picker';
import { ExperimentService } from '@features/experiment/services/experiment.service';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { DialogLayout, DialogMode } from '@ui/dialog/dialog-layout';
import { SubmitButton } from '@ui/dialog/submit-button';
import { DismissButton } from '@ui/dismiss-button/dismiss-button';
import { FieldErrors } from '@ui/field-errors/field-errors';
import { experimentSchema, toFormModel, toWriteDto } from './experiment-form';

@Component({
  selector: 'app-experiment-dialog',
  providers: [provideNativeDateAdapter()],
  imports: [
    DialogLayout,
    SubmitButton,
    DismissButton,
    MatFormField,
    MatLabel,
    MatInput,
    MatError,
    MatSuffix,
    MatDatepicker,
    MatDatepickerInput,
    MatDatepickerToggle,
    FormField,
    FormRoot,
    TranslatePipe,
    ExperimentDocuments,
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
  private readonly permissions = inject(PermissionsService);
  private readonly formErrorService = inject(FormErrorService);
  private readonly toastService = inject(ToastService);
  private readonly translateService = inject(TranslateService);
  private readonly dialogRef = inject<MatDialogRef<ExperimentDialog>>(MatDialogRef);

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

  // owners are picked from the PIs, and a new experiment gets its PI group in Keycloak only later
  protected readonly canManageOwners = computed(
    () => this.mode() === 'edit' && this.permissions.isAdmin(),
  );
  protected readonly owners = computed(() => this.experiment()?.owners ?? []);
  protected readonly ownersUnavailable = computed(
    () => this.experiment()?.ownersUnavailable ?? false,
  );
  // compared by content: a saved experiment with the same owners must not reset the selection
  private readonly storedOwnerIds = computed(() => this.owners().map((owner) => owner.id!), {
    equal: sameIds,
  });
  protected readonly ownerIds = linkedSignal(() => this.storedOwnerIds());
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
    try {
      const saved = await this.experimentService.saveExperiment(
        toWriteDto(this.formModel(), this.experiment()),
      );
      // keeps the new version, a retry after a failed owner save would conflict otherwise
      this.experiment.set(saved);
    } catch (error) {
      return this.formErrorService.mapApiErrorsToFormErrors(
        toErrorDtos(error),
        this.experimentForm,
        'experiment.error.unspecified.message',
      );
    }
    if (!(await this.saveOwners())) return undefined;
    this.toastService.success(this.translateService.translate('experiment.success')());
    afterSave();
    return undefined;
  }

  /** False when saving the owners failed, the dialog then stays open with the error. */
  private async saveOwners(): Promise<boolean> {
    const experiment = this.experiment();
    this.ownerError.set(undefined);
    if (
      !experiment?.id ||
      !this.canManageOwners() ||
      sameIds(this.storedOwnerIds(), this.ownerIds())
    )
      return true;
    try {
      this.experiment.set(
        await this.experimentService.replaceOwners(experiment.id, this.ownerIds()),
      );
      return true;
    } catch (error) {
      this.ownerError.set(toErrorDtos(error)[0]?.messageKey ?? 'error.user-directory.unavailable');
      return false;
    }
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

function sameIds(a: string[], b: string[]): boolean {
  const set = new Set(a);
  return set.size === b.length && b.every((id) => set.has(id));
}
