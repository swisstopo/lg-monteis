import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatDialogRef } from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { TranslatePipe } from '@ngx-translate/core';

/**
 * Cancel or Close. Inside a dialog it closes that dialog, anywhere it emits `dismissed`, so a
 * panel or a toast can use it as well.
 */
@Component({
  selector: 'app-dismiss-button',
  imports: [MatButton, MatIcon, TranslatePipe],
  template: `
    <button type="button" [matButton]="kind() === 'close' ? 'filled' : 'text'" (click)="dismiss()">
      <mat-icon [fontIcon]="kind()" />
      {{ 'button.' + kind() | translate }}
    </button>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DismissButton {
  private readonly dialogRef = inject(MatDialogRef, { optional: true });

  // close is the primary action of a view, cancel a quiet way out of a form
  readonly kind = input<'cancel' | 'close'>('cancel');
  readonly dismissed = output<void>();

  protected dismiss(): void {
    this.dismissed.emit();
    this.dialogRef?.close();
  }
}
