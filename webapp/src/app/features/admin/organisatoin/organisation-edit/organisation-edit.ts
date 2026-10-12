import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  linkedSignal,
  resource,
} from '@angular/core';
import {
  apply,
  form,
  FormField,
  FormRoot,
  submit,
  TreeValidationResult,
} from '@angular/forms/signals';
import { MatDialogRef } from '@angular/material/dialog';
import { MatError, MatFormField, MatInput, MatLabel } from '@angular/material/input';
import { toErrorDtos } from '@core/http/api-error.model';
import { ToastService } from '@core/notifications/toast.service';
import { FormErrorService } from '@core/utils/form-error.service';
import { OrganisationService } from '@features/admin/organisatoin/services/organisation.service';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { DialogLayout, DialogMode } from '@ui/dialog/dialog-layout';
import { SubmitButton } from '@ui/dialog/submit-button';
import { DismissButton } from '@ui/dismiss-button/dismiss-button';
import { FieldErrors } from '@ui/field-errors/field-errors';
import { organisationSchema, toFormModel, toWriteDto } from './organisation-form';

@Component({
  selector: 'app-organisation-edit',
  imports: [
    DialogLayout,
    SubmitButton,
    DismissButton,
    MatFormField,
    MatLabel,
    MatInput,
    MatError,
    FormField,
    FormRoot,
    TranslatePipe,
    FieldErrors,
  ],
  templateUrl: './organisation-edit.html',
  styleUrl: './organisation-edit.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class OrganisationEdit {
  private readonly organisationService = inject(OrganisationService);
  private readonly formErrorService = inject(FormErrorService);
  private readonly toastService = inject(ToastService);
  private readonly translateService = inject(TranslateService);
  private readonly dialogRef = inject<MatDialogRef<OrganisationEdit>>(MatDialogRef);

  readonly organisationId = input<string>();

  protected readonly mode = computed<DialogMode>(() =>
    this.organisationId() === undefined ? 'create' : 'edit',
  );

  // no error handling here, the restErrorInterceptor already toasts a failed load
  private readonly loadedOrganisation = resource({
    params: () => this.organisationId(),
    loader: ({ params: id }) => this.organisationService.getOrganisation(id),
  });

  // undefined again once "save and create new" starts the next organisation
  private readonly organisation = linkedSignal(() =>
    this.loadedOrganisation.hasValue() ? this.loadedOrganisation.value() : undefined,
  );

  private readonly formModel = linkedSignal(() => toFormModel(this.organisation()));

  protected readonly organisationForm = form(
    this.formModel,
    (path) => apply(path, organisationSchema(this.translateService)),
    { submission: { action: () => this.save(() => this.dialogRef.close()) } },
  );

  protected saveAndCreateNew(): Promise<boolean> {
    return submit(this.organisationForm, () => this.save(() => this.startNextOrganisation()));
  }

  private async save(afterSave: () => void): Promise<TreeValidationResult> {
    try {
      await this.organisationService.saveOrganisation(
        this.organisation()?.id,
        toWriteDto(this.formModel()),
      );
    } catch (error) {
      return this.formErrorService.mapApiErrorsToFormErrors(
        toErrorDtos(error),
        this.organisationForm,
        'organisation.error.unspecified.message',
      );
    }
    this.toastService.success(this.translateService.translate('organisation.success')());
    afterSave();
    return undefined;
  }

  // the form model is set as well: in create mode organisation already is undefined, setting it
  // again would not recompute the form model
  private startNextOrganisation(): void {
    this.organisation.set(undefined);
    this.formModel.set(toFormModel(undefined));
    this.organisationForm().reset();
  }
}
