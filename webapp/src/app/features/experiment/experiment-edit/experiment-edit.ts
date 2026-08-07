import { Component, computed, effect, inject, input, linkedSignal, signal } from '@angular/core';
import {
  form,
  FormField,
  maxLength,
  minLength,
  required,
  submit,
  validate,
} from '@angular/forms/signals';
import { MatButton } from '@angular/material/button';
import { MatOption, provideNativeDateAdapter } from '@angular/material/core';
import {
  MatDatepicker,
  MatDatepickerInput,
  MatDatepickerToggle,
} from '@angular/material/datepicker';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatError, MatFormField, MatInput, MatLabel } from '@angular/material/input';
import { MatSelect } from '@angular/material/select';
import { translate, TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ExperimentResponseDto, WriteExperimentDto } from '../../../core/generated';
import { toErrorDtos } from '../../../shared/models/api-error.model';
import { FormErrorService } from '../../../shared/services/form-error.service';
import { ToastService } from '../../../shared/services/toast.service';
import { getStatusMetadata, Status } from '../models/experiment.model';
import { ExperimentService } from '../services/experiment.service';

interface ExperimentFormData {
  comment: string;
  experimentDates: {
    experimentStart: Date;
    experimentEnd: Date;
  };
  name: string;
  status: Status;
}

function domainModelToFormModel(domainModel: ExperimentResponseDto): ExperimentFormData {
  return {
    name: domainModel.name ?? '',
    comment: domainModel.comment ?? '',
    experimentDates: {
      experimentStart: domainModel.experimentDates?.experimentStart
        ? new Date(domainModel.experimentDates.experimentStart)
        : new Date(),
      experimentEnd: domainModel.experimentDates?.experimentEnd
        ? new Date(domainModel.experimentDates.experimentEnd)
        : new Date(),
    },
    status: domainModel.status ?? WriteExperimentDto.StatusEnum.Active,
  };
}

@Component({
  selector: 'app-experiment-edit',
  standalone: true,
  providers: [provideNativeDateAdapter()],
  imports: [
    MatError,
    MatFormField,
    MatFormFieldModule,
    MatLabel,
    MatInput,
    MatButton,
    MatSelect,
    MatOption,
    FormField,
    MatDialogModule,
    MatIcon,
    TranslatePipe,
    MatDatepickerInput,
    MatDatepickerToggle,
    MatDatepicker,
  ],
  templateUrl: './experiment-edit.html',
  styleUrl: './experiment-edit.scss',
})
export default class ExperimentEdit {
  private readonly experimentService = inject(ExperimentService);
  private readonly toastService = inject(ToastService);
  private readonly i18nService = inject(TranslateService);
  private readonly formErrorService = inject(FormErrorService);
  readonly dialogRef = inject<MatDialogRef<ExperimentEdit>>(MatDialogRef, {
    optional: true,
  });
  readonly experimentId = input<number | undefined>(undefined);

  readonly statusValues = Object.values(WriteExperimentDto.StatusEnum) as Status[];
  readonly statusMetadata = getStatusMetadata();

  readonly saveError = this.experimentService.error;
  experiment = signal<ExperimentResponseDto | undefined>(undefined);
  title = computed(() =>
    this.experimentId()
      ? this.i18nService.translate('experiment.edit.title.edit')()
      : this.i18nService.translate('experiment.edit.title.create')(),
  );

  private readonly syncSelectedExperiment = effect(() => {
    this.experimentService.getExperiment(this.experimentId());
  });

  private readonly applyLoadedExperiment = effect(() => {
    try {
      this.experiment.set(this.experimentService.experiment.value());
      if (this.experiment() !== undefined) {
        this.domainModel.set(this.experiment()!);
        this.experimentForm().markAsTouched();
      }
    } catch {
      const errors = toErrorDtos(this.experimentService.experiment.error());
      errors.forEach((err) =>
        this.toastService.error(
          translate(err?.messageKey ?? 'experiment.error.unspecified.message')(),
          translate('experiment.error.unspecified.title')(),
        ),
      );
    }
  });

  protected close(): void {
    this.dialogRef?.close();
  }

  readonly domainModel = signal<ExperimentResponseDto>({});
  private readonly formModel = linkedSignal({
    source: this.domainModel,
    computation: (domainModel) =>
      domainModel ? domainModelToFormModel(domainModel) : this.initExperimentModel(),
  });

  private initExperimentModel(): ExperimentFormData {
    return {
      name: '',
      status: WriteExperimentDto.StatusEnum.Active,
      comment: '',
      experimentDates: {
        experimentStart: new Date(),
        experimentEnd: new Date(),
      },
    };
  }

  readonly experimentForm = form(this.formModel, (schema) => {
    required(schema.name, { message: translate('experiment.name.validation.required')() });
    minLength(schema.name, 2, { message: translate('experiment.name.validation.minLength')() });
    maxLength(schema.name, 50, { message: translate('experiment.name.validation.maxLength')() });
    required(schema.experimentDates.experimentStart, {
      message: translate('experiment.experimentDates.experimentStart.validation.required')(),
    });
    required(schema.experimentDates.experimentEnd, {
      message: translate('experiment.experimentDates.experimentEnd.validation.required')(),
    });
    required(schema.status, { message: translate('experiment.status.validation.required')() });
    validate(schema.experimentDates.experimentStart, ({ value, valueOf }) => {
      const experimentStart = value();
      const experimentEnd = valueOf(schema.experimentDates.experimentEnd);
      if (experimentStart > experimentEnd) {
        return {
          kind: 'bounds',
          message: this.i18nService.translate('experiment.alarmLimit.from.validation.bounds')(),
        };
      }
      return undefined;
    });
    validate(schema.experimentDates.experimentEnd, ({ value, valueOf }) => {
      const experimentEnd = value();
      const experimentStart = valueOf(schema.experimentDates.experimentStart);
      if (experimentEnd > experimentStart) {
        return {
          kind: 'bounds',
          message: this.i18nService.translate('sensor.experimentDate.to.validation.bounds')(),
        };
      }
      return undefined;
    });
  });

  async onSubmit(event: SubmitEvent) {
    event.preventDefault();
    const submitter = event.submitter as HTMLButtonElement | null;
    const resetAfter = submitter?.dataset['action'] === 'saveAndCreate';

    if (this.experimentForm().invalid()) {
      this.experimentForm().markAsTouched();
      return;
    }
    await submit(this.experimentForm, async (field) => {
      const experiment = this.buildPayload(this.formModel());

      try {
        await this.saveExperiment(experiment);

        this.toastService.success(this.i18nService.instant('experiment.success'));

        if (resetAfter) {
          this.resetForm();
        } else {
          this.dialogRef?.close();
        }
        return;
      } catch {
        return this.formErrorService.mapApiErrorsToFormErrors(
          this.saveError(),
          this.experimentForm,
          'experiment.error.unspecified.message',
        );
      }
    });
  }

  private resetForm(): void {
    this.domainModel.set({});
    this.experimentService.getExperiment(undefined);
    this.experimentForm().reset();
  }

  private buildPayload(formData: ExperimentFormData): WriteExperimentDto {
    return {
      id: this.experiment()?.id ?? undefined,
      status: formData.status,
      name: formData.name,
      comment: formData.comment,
      experimentDates: {
        experimentStart: this.toLocalDateString(formData.experimentDates.experimentStart),
        experimentEnd: this.toLocalDateString(formData.experimentDates.experimentEnd),
      },
      version: this.experiment()?.version ?? undefined,
    };
  }

  private async saveExperiment(experiment: WriteExperimentDto) {
    if (experiment.id) {
      return await this.experimentService.updateExperiment(experiment.id, experiment);
    } else {
      return await this.experimentService.createExperiment(experiment);
    }
  }

  private toLocalDateString(date: Date): string {
    const offset = date.getTimezoneOffset();
    const localDate = new Date(date.getTime() - offset * 60 * 1000);
    return localDate.toISOString().split('T')[0];
  }
}
