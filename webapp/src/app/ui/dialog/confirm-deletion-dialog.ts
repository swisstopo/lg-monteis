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
  templateUrl: './confirm-deletion-dialog.html',
  styleUrl: './confirm-deletion-dialog.scss',
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
