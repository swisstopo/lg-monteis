import { maxLength, minLengthError, required, schema, validate } from '@angular/forms/signals';
import { OrganisationResponseDto, WriteOrganisationDto } from '@core/generated';
import { TranslateService } from '@ngx-translate/core';

// the same limits as WriteOrganisationDto, the backend rejects anything the form would let through
const NAME_MIN_LENGTH = 2;
const NAME_MAX_LENGTH = 100;
const COMMENT_MAX_LENGTH = 4096;

export interface OrganisationFormModel {
  name: string;
  comment: string;
}

export function toFormModel(
  organisation: OrganisationResponseDto | undefined,
): OrganisationFormModel {
  return {
    name: organisation?.name ?? '',
    comment: organisation?.comment ?? '',
  };
}

export function toWriteDto(model: OrganisationFormModel): WriteOrganisationDto {
  return {
    name: model.name.trim(),
    comment: model.comment.trim() || undefined,
  };
}

// the messages are signals, a language switch then updates errors that are already shown
export function organisationSchema(translateService: TranslateService) {
  return schema<OrganisationFormModel>((path) => {
    required(path.name, {
      message: translateService.translate('organisation.name.validation.required'),
    });
    // the trimmed name is what gets saved, so that is what has to be long enough. An empty name
    // is left to required
    validate(path.name, ({ value }) =>
      value() !== '' && value().trim().length < NAME_MIN_LENGTH
        ? minLengthError(NAME_MIN_LENGTH, {
            message: translateService.translate('organisation.name.validation.minLength', {
              min: NAME_MIN_LENGTH,
            })(),
          })
        : undefined,
    );
    maxLength(path.name, NAME_MAX_LENGTH, {
      message: translateService.translate('organisation.name.validation.maxLength', {
        max: NAME_MAX_LENGTH,
      }),
    });
    maxLength(path.comment, COMMENT_MAX_LENGTH, {
      message: translateService.translate('organisation.comment.validation.maxLength', {
        max: COMMENT_MAX_LENGTH,
      }),
    });
  });
}
