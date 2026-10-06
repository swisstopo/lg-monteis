import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  inputBinding,
  linkedSignal,
  resource,
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
import { MatButton } from '@angular/material/button';
import { provideNativeDateAdapter } from '@angular/material/core';
import {
  MatDatepicker,
  MatDatepickerInput,
  MatDatepickerToggle,
} from '@angular/material/datepicker';
import { MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { MatError, MatFormField, MatInput, MatLabel, MatSuffix } from '@angular/material/input';
import { PermissionsService } from '@core/auth/permissions.service';
import { toErrorDtos } from '@core/http/api-error.model';
import { ToastService } from '@core/notifications/toast.service';
import { FormErrorService } from '@core/utils/form-error.service';
import { ExperimentDocuments } from '@features/experiment/experiment-documents/experiment-documents';
import { ExperimentService } from '@features/experiment/services/experiment.service';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { FORM_DIALOG_CONFIG } from '@ui/dialog/form-dialog.config';
import { FieldErrors } from '@ui/field-errors/field-errors';
import { experimentSchema, toFormModel, toWriteDto } from './experiment-form';

type DialogMode = 'create' | 'edit' | 'view';

/** An existing experiment to open, read-only with `viewOnly` even if the caller may write it. */
export interface ExperimentDialogTarget {
  experimentId: string;
  viewOnly: boolean;
}

/** Opens the dialog for a new experiment, or for `target`. */
export function openExperimentDialog(dialog: MatDialog, target?: ExperimentDialogTarget) {
  return dialog.open(ExperimentDialog, {
    ...FORM_DIALOG_CONFIG,
    bindings: target
      ? [
          inputBinding('experimentId', () => target.experimentId),
          inputBinding('viewOnly', () => target.viewOnly),
        ]
      : [],
  });
}

@Component({
  selector: 'app-experiment-dialog',
  providers: [provideNativeDateAdapter()],
  imports: [
    MatDialogModule,
    MatFormField,
    MatLabel,
    MatInput,
    MatError,
    MatSuffix,
    MatDatepicker,
    MatDatepickerInput,
    MatDatepickerToggle,
    MatButton,
    MatIcon,
    FormField,
    FormRoot,
    TranslatePipe,
    ExperimentDocuments,
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

  protected readonly experimentForm = form(
    this.formModel,
    (path) => {
      apply(path, experimentSchema(this.translateService));
      disabled(path, this.readOnly);
    },
    { submission: { action: () => this.save(() => this.dialogRef.close()) } },
  );

  protected readonly saveDisabled = computed(
    () => this.experimentForm().submitting() || this.experimentForm().invalid(),
  );

  protected saveAndCreateNew(): Promise<boolean> {
    return submit(this.experimentForm, () => this.save(() => this.startNextExperiment()));
  }

  private async save(afterSave: () => void): Promise<TreeValidationResult> {
    try {
      await this.experimentService.saveExperiment(toWriteDto(this.formModel(), this.experiment()));
    } catch (error) {
      return this.formErrorService.mapApiErrorsToFormErrors(
        toErrorDtos(error),
        this.experimentForm,
        'experiment.error.unspecified.message',
      );
    }
    this.toastService.success(this.translateService.translate('experiment.success')());
    afterSave();
    return undefined;
  }

  // the form model is set as well: in create mode experiment already is undefined, setting it
  // again would not recompute the form model
  private startNextExperiment(): void {
    this.experiment.set(undefined);
    this.formModel.set(toFormModel(undefined));
    this.experimentForm().reset();
  }
}
