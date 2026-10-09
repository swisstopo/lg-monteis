import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { FieldTree } from '@angular/forms/signals';

/**
 * The messages of a signal-form field, as the content of its mat-error. On the mat-error itself,
 * the form field only projects a direct mat-error child, and only while the control is in error.
 */
@Component({
  selector: 'mat-error[appFieldErrors]',
  template: `@for (error of field()().errors(); track error) {
    {{ error.message }}
  }`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FieldErrors {
  readonly field = input.required<FieldTree<unknown>>({ alias: 'appFieldErrors' });
}
