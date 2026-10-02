import { maxDate, maxLength, minDate, minLength, required, schema } from '@angular/forms/signals';
import { ExperimentResponseDto, WriteExperimentDto } from '@core/generated';
import { TranslateService } from '@ngx-translate/core';
import { formatDate } from 'date-fns';

export interface ExperimentFormModel {
  name: string;
  comment: string;
  period: {
    start: Date;
    end: Date;
  };
}

export function toFormModel(experiment: ExperimentResponseDto | undefined): ExperimentFormModel {
  return {
    name: experiment?.name ?? '',
    comment: experiment?.comment ?? '',
    period: {
      start: toDate(experiment?.period?.start),
      end: toDate(experiment?.period?.end),
    },
  };
}

/** The payload that saves `model`, as an update of `experiment` if there is one. */
export function toWriteDto(
  model: ExperimentFormModel,
  experiment: ExperimentResponseDto | undefined,
): WriteExperimentDto {
  return {
    id: experiment?.id,
    version: experiment?.version,
    name: model.name,
    comment: model.comment.trim() || undefined,
    period: {
      start: toIsoDate(model.period.start),
      end: toIsoDate(model.period.end),
    },
  };
}

// the messages are signals, so they follow a language switch
export function experimentSchema(i18n: TranslateService) {
  return schema<ExperimentFormModel>((path) => {
    required(path.name, { message: i18n.translate('experiment.name.validation.required') });
    minLength(path.name, 2, { message: i18n.translate('experiment.name.validation.minLength') });
    maxLength(path.name, 50, { message: i18n.translate('experiment.name.validation.maxLength') });
    maxLength(path.comment, 4096, {
      message: i18n.translate('experiment.comment.validation.maxLength'),
    });
    required(path.period.start, {
      message: i18n.translate('experiment.period.start.validation.required'),
    });
    required(path.period.end, {
      message: i18n.translate('experiment.period.end.validation.required'),
    });
    maxDate(path.period.start, ({ valueOf }) => valueOf(path.period.end), {
      message: i18n.translate('experiment.experimentDate.from.validation.bounds'),
    });
    minDate(path.period.end, ({ valueOf }) => valueOf(path.period.start), {
      message: i18n.translate('experiment.experimentDate.to.validation.bounds'),
    });
  });
}

function toDate(isoDate: string | undefined): Date {
  return isoDate ? new Date(isoDate) : new Date();
}

function toIsoDate(date: Date): string {
  return formatDate(date, 'yyyy-MM-dd');
}
