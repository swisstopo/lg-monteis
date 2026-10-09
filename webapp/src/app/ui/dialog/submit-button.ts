import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { FieldTree } from '@angular/forms/signals';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';

/**
 * A button that submits `form`, disabled while it is invalid or submitting. The label is its
 * content. With `formId` it submits that form element (Enter works too), without one it only
 * emits `pressed`, e.g. for "save and create new".
 */
@Component({
  selector: 'app-submit-button',
  imports: [MatButton, MatIcon],
  template: `
    <button
      [matButton]="variant() === 'primary' ? 'filled' : 'tonal'"
      [type]="formId() ? 'submit' : 'button'"
      [attr.form]="formId() ?? null"
      [disabled]="form()().submitting() || form()().invalid()"
      (click)="pressed.emit()"
    >
      <mat-icon fontIcon="save" />
      <ng-content />
    </button>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SubmitButton {
  readonly form = input.required<FieldTree<unknown>>();
  readonly variant = input<'primary' | 'secondary'>('primary');
  readonly formId = input<string>();
  readonly pressed = output<void>();
}
