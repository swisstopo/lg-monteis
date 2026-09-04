import { inject, Injectable } from '@angular/core';
import { FieldTree } from '@angular/forms/signals';
import { ErrorDto } from '@core/generated';
import { ToastService } from '@core/notifications/toast.service';
import { TranslateService } from '@ngx-translate/core';

export interface FormFieldServerError {
  kind: 'serverError';
  message: string;
  fieldTree: FieldTree<unknown>;
}

/**
 * Maps API error DTOs to signal-form server errors.
 * Errors that reference a known form field are attached to that field;
 * unmapped errors are shown as toast notifications.
 */
@Injectable({ providedIn: 'root' })
export class FormErrorService {
  private readonly translateService = inject(TranslateService);
  private readonly toastService = inject(ToastService);

  mapApiErrorsToFormErrors<TForm extends FieldTree<unknown>>(
    errors: ErrorDto[] | undefined,
    form: TForm,
    fallbackMessageKey = 'error.system.generic',
  ): FormFieldServerError[] | undefined {
    if (!errors) {
      return undefined;
    }

    return errors
      .map((err) => {
        const mappedField = err?.field
          ? err.field
              .split('.')
              .reduce<FieldTree<unknown> | undefined>(
                (node, key) => (node as Record<string, FieldTree<unknown>> | undefined)?.[key],
                form,
              )
          : undefined;

        if (mappedField) {
          return {
            kind: 'serverError' as const,
            message: this.translateService.translate(err.messageKey ?? fallbackMessageKey)(),
            fieldTree: mappedField,
          };
        }

        this.toastService.error(
          this.translateService.translate(err.messageKey ?? fallbackMessageKey, err.params)(),
        );
        return undefined;
      })
      .filter((entry): entry is NonNullable<typeof entry> => entry !== undefined);
  }
}
