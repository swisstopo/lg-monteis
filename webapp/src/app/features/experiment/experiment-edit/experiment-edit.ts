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
import { provideNativeDateAdapter } from '@angular/material/core';
import {
  MatDatepicker,
  MatDatepickerInput,
  MatDatepickerToggle,
} from '@angular/material/datepicker';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatError, MatFormField, MatInput, MatLabel } from '@angular/material/input';
import { ExperimentResponseDto, WriteExperimentDto } from '@core/generated';
import { toErrorDtos } from '@core/http/api-error.model';
import { ToastService } from '@core/notifications/toast.service';
import { FormErrorService } from '@core/utils/form-error.service';
import { ExperimentService } from '@features/experiment/services/experiment.service';
import { translate, TranslatePipe, TranslateService } from '@ngx-translate/core';
import { formatDate } from 'date-fns';

interface ExperimentFormData {
  comment: string;
  period: {
    start: Date;
    end: Date;
  };
  name: string;
}

function domainModelToFormModel(domainModel: ExperimentResponseDto): ExperimentFormData {
  return {
    name: domainModel.name ?? '',
    comment: domainModel.comment ?? '',
    period: {
      start: domainModel.period?.start ? new Date(domainModel.period.start) : new Date(),
      end: domainModel.period?.end ? new Date(domainModel.period.end) : new Date(),
    },
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
  private readonly translateService = inject(TranslateService);
  private readonly formErrorService = inject(FormErrorService);
  readonly dialogRef = inject<MatDialogRef<ExperimentEdit>>(MatDialogRef, {
    optional: true,
  });
  readonly experimentId = input<string | undefined>(undefined);

  readonly saveError = this.experimentService.error;
  experiment = signal<ExperimentResponseDto | undefined>(undefined);
  title = computed(() =>
    this.experimentId()
      ? this.translateService.translate('experiment.edit.title.edit')()
      : this.translateService.translate('experiment.edit.title.create')(),
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

  readonly domainModel = signal<ExperimentResponseDto>({});
  private readonly formModel = linkedSignal({
    source: this.domainModel,
    computation: (domainModel) =>
      domainModel ? domainModelToFormModel(domainModel) : this.initExperimentModel(),
  });

  private initExperimentModel(): ExperimentFormData {
    return {
      name: '',
      comment: '',
      period: {
        start: new Date(),
        end: new Date(),
      },
    };
  }

  readonly experimentForm = form(this.formModel, (schema) => {
    required(schema.name, { message: translate('experiment.name.validation.required')() });
    minLength(schema.name, 2, { message: translate('experiment.name.validation.minLength')() });
    maxLength(schema.name, 50, { message: translate('experiment.name.validation.maxLength')() });
    required(schema.period.start, {
      message: translate('experiment.period.start.validation.required')(),
    });
    required(schema.period.end, {
      message: translate('experiment.period.end.validation.required')(),
    });
    validate(schema.period.start, ({ value, valueOf }) => {
      const start = value();
      const end = valueOf(schema.period.end);
      if (start > end) {
        return {
          kind: 'bounds',
          message: this.translateService.translate(
            'experiment.experimentDate.from.validation.bounds',
          )(),
        };
      }
      return undefined;
    });
    validate(schema.period.end, ({ value, valueOf }) => {
      const end = value();
      const start = valueOf(schema.period.start);
      if (end < start) {
        return {
          kind: 'bounds',
          message: this.translateService.translate(
            'experiment.experimentDate.to.validation.bounds',
          )(),
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

        this.toastService.success(this.translateService.translate('experiment.success')());

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
    const cleanedComment = formData.comment?.trim() || undefined;

    return {
      id: this.experiment()?.id ?? undefined,
      name: formData.name,
      comment: cleanedComment,
      period: {
        start: formatDate(formData.period.start, 'yyyy-MM-dd'),
        end: formatDate(formData.period.end, 'yyyy-MM-dd'),
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
}
