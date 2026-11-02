import {
  ChangeDetectionStrategy,
  Component,
  inject,
  Injectable,
  input,
  inputBinding,
} from '@angular/core';
import { MatButton } from '@angular/material/button';
import {
  MatDialog,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { DismissButton } from '@ui/dismiss-button/dismiss-button';
import { firstValueFrom } from 'rxjs';
import { DialogInputs } from './form-dialog.service';

/** Asks before deleting something for good, closes with `true` when confirmed. */
@Component({
  selector: 'app-confirm-deletion-dialog',
  imports: [
    DismissButton,
    MatButton,
    MatDialogActions,
    MatDialogClose,
    MatDialogContent,
    MatDialogTitle,
    MatIcon,
  ],
  template: `
    <h2 mat-dialog-title>{{ title() }}</h2>
    <mat-dialog-content>{{ message() }}</mat-dialog-content>
    <mat-dialog-actions align="end">
      <app-dismiss-button />
      <button matButton="filled" type="button" class="confirm" [mat-dialog-close]="true">
        <mat-icon fontIcon="delete" />
        {{ confirmLabel() }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .confirm {
      --mat-button-filled-container-color: var(--mat-sys-error);
      --mat-button-filled-label-text-color: var(--mat-sys-on-error);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmDeletionDialog {
  readonly title = input.required<string>();
  readonly message = input.required<string>();
  readonly confirmLabel = input.required<string>();
}

@Injectable({ providedIn: 'root' })
export class ConfirmDeletionService {
  private readonly dialog = inject(MatDialog);

  async confirmDeletion(inputs: Required<DialogInputs<ConfirmDeletionDialog>>): Promise<boolean> {
    const bindings = Object.entries(inputs).map(([name, value]) => inputBinding(name, () => value));
    const dialogRef = this.dialog.open<ConfirmDeletionDialog, unknown, boolean>(
      ConfirmDeletionDialog,
      {
        bindings,
        autoFocus: 'dialog',
      },
    );
    return (await firstValueFrom(dialogRef.afterClosed())) === true;
  }
}
