import { Component, Input } from '@angular/core';
import { AbstractControl } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';

@Component({
  selector: 'app-form-error',
  standalone: true,
  imports: [TranslatePipe],
  template: `
    @if (backendError) {
      {{ backendError.messageKey | translate: backendError.params }}
    }
  `,
})
export class FormErrorComponent {
  @Input({ required: true }) control!: AbstractControl | null;

  get backendError() {
    return this.control?.getError('backend');
  }
}
