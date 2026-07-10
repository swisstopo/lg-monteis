import { Injectable } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { ErrorDTO } from '../generated';

@Injectable({
  providedIn: 'root',
})
export class FormErrorService {
  applyErrors(form: FormGroup, errors: ErrorDTO[]): void {
    for (const error of errors) {
      if (!error.field || error.field === 'global') {
        continue;
      }

      const control = form.get(error.field);

      if (!control) {
        console.warn(`Backend returned unknown field ${error.field}`);
        continue;
      }

      control.setErrors({
        ...(control.errors ?? {}),
        backend: {
          messageKey: error.messageKey,
          params: error.params,
        },
      });

      control.markAsTouched();
      control.markAsDirty();
    }
  }
}
