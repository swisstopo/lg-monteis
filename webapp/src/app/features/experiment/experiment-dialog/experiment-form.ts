import { maxDate, maxLength, minDate, minLength, required, schema } from '@angular/forms/signals';
import { ExperimentResponseDto, WriteExperimentDto } from '@core/generated';
import { TranslateService } from '@ngx-translate/core';
import { formatDate, parseISO } from 'date-fns';

// the backend validates the same limits (WriteExperimentDto)
const NAME_MIN_LENGTH = 2;
const NAME_MAX_LENGTH = 50;
const COMMENT_MAX_LENGTH = 4096;

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
export function experimentSchema(translateService: TranslateService) {
  return schema<ExperimentFormModel>((path) => {
    required(path.name, {
      message: translateService.translate('experiment.name.validation.required'),
    });
    minLength(path.name, NAME_MIN_LENGTH, {
      message: translateService.translate('experiment.name.validation.minLength', {
        min: NAME_MIN_LENGTH,
      }),
    });
    maxLength(path.name, NAME_MAX_LENGTH, {
      message: translateService.translate('experiment.name.validation.maxLength', {
        max: NAME_MAX_LENGTH,
      }),
    });
    maxLength(path.comment, COMMENT_MAX_LENGTH, {
      message: translateService.translate('experiment.comment.validation.maxLength', {
        max: COMMENT_MAX_LENGTH,
      }),
    });
    required(path.period.start, {
      message: translateService.translate('experiment.period.start.validation.required'),
    });
    required(path.period.end, {
      message: translateService.translate('experiment.period.end.validation.required'),
    });
    maxDate(path.period.start, ({ valueOf }) => valueOf(path.period.end), {
      message: translateService.translate('experiment.period.start.validation.bounds'),
    });
    minDate(path.period.end, ({ valueOf }) => valueOf(path.period.start), {
      message: translateService.translate('experiment.period.end.validation.bounds'),
    });
  });
}

// parseISO reads a date-only string as local midnight, new Date() as UTC midnight, which is the
// day before west of UTC
function toDate(isoDate: string | undefined): Date {
  return isoDate ? parseISO(isoDate) : new Date();
}

function toIsoDate(date: Date): string {
  return formatDate(date, 'yyyy-MM-dd');
}
