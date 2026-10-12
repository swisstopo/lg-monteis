import { Component, computed, effect, inject, input, linkedSignal, signal } from '@angular/core';
import {
  form,
  FormField,
  maxLength,
  minLengthError,
  required,
  submit,
  validate,
} from '@angular/forms/signals';
import { MatButton } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { MatError, MatFormField, MatInput, MatLabel } from '@angular/material/input';
import { OrganisationResponseDto, WriteOrganisationDto } from '@core/generated';
import { ToastService } from '@core/notifications/toast.service';
import { FormErrorService } from '@core/utils/form-error.service';
import { OrganisationService } from '@features/admin/organisatoin/services/organisation.service';
import { translate, TranslatePipe, TranslateService } from '@ngx-translate/core';

interface OrganisationFormData {
  name: string;
  comment: string;
}

@Component({
  selector: 'app-organisation-edit',
  imports: [
    MatError,
    MatFormField,
    MatLabel,
    MatInput,
    MatButton,
    FormField,
    MatDialogModule,
    MatIcon,
    TranslatePipe,
  ],
  templateUrl: './organisation-edit.html',
  styleUrl: './organisation-edit.scss',
})
export default class OrganisationEdit {
  private readonly organisationService = inject(OrganisationService);
  private readonly toastService = inject(ToastService);
  private readonly translateService = inject(TranslateService);
  private readonly formErrorService = inject(FormErrorService);
  readonly dialogRef = inject<MatDialogRef<OrganisationEdit>>(MatDialogRef, { optional: true });
  readonly organisationId = input<string | undefined>(undefined);

  readonly saveError = this.organisationService.error;
  private readonly organisation = signal<OrganisationResponseDto | undefined>(undefined);
  protected readonly title = computed(() =>
    this.organisationId()
      ? this.translateService.translate('organisation.edit.title.edit')()
      : this.translateService.translate('organisation.edit.title.create')(),
  );

  private readonly syncSelectedOrganisation = effect(() => {
    this.organisationService.getOrganisation(this.organisationId());
  });

  private readonly applyLoadedOrganisation = effect(() => {
    if (!this.organisationService.organisation.hasValue()) return;
    const loaded = this.organisationService.organisation.value();
    if (loaded === undefined) return;
    this.organisation.set(loaded);
    this.organisationForm().markAsTouched();
  });

  private readonly formModel = linkedSignal<
    OrganisationResponseDto | undefined,
    OrganisationFormData
  >({
    source: this.organisation,
    computation: (organisation) => ({
      name: organisation?.name ?? '',
      comment: organisation?.comment ?? '',
    }),
  });

  readonly organisationForm = form(this.formModel, (schema) => {
    required(schema.name, { message: translate('organisation.name.validation.required')() });
    // the trimmed name is what gets saved, so that is what has to be long enough
    validate(schema.name, ({ value }) =>
      value().trim().length < 2
        ? minLengthError(2, { message: translate('organisation.name.validation.minLength')() })
        : undefined,
    );
    maxLength(schema.name, 100, { message: translate('organisation.name.validation.maxLength')() });
    maxLength(schema.comment, 4096, {
      message: translate('organisation.comment.validation.maxLength')(),
    });
  });

  async onSubmit(event: SubmitEvent) {
    event.preventDefault();
    const submitter = event.submitter as HTMLButtonElement | null;
    const resetAfter = submitter?.dataset['action'] === 'saveAndCreate';

    if (this.organisationForm().invalid()) {
      this.organisationForm().markAsTouched();
      return;
    }
    await submit(this.organisationForm, async () => {
      const payload: WriteOrganisationDto = {
        name: this.formModel().name.trim(),
        comment: this.formModel().comment?.trim() || undefined,
      };
      try {
        const id = this.organisation()?.id;
        if (id) {
          await this.organisationService.updateOrganisation(id, payload);
        } else {
          await this.organisationService.createOrganisation(payload);
        }
        this.toastService.success(this.translateService.translate('organisation.success')());

        if (resetAfter) {
          this.resetForm();
        } else {
          this.dialogRef?.close();
        }
        return;
      } catch {
        return this.formErrorService.mapApiErrorsToFormErrors(
          this.saveError(),
          this.organisationForm,
          'organisation.error.unspecified.message',
        );
      }
    });
  }

  private resetForm(): void {
    this.organisation.set(undefined);
    this.organisationService.getOrganisation(undefined);
    this.organisationForm().reset({ name: '', comment: '' });
  }
}
