import { Component, inject, input, inputBinding } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { TranslatePipe } from '@ngx-translate/core';
import ExperimentEdit from '../experiment-edit/experiment-edit';

@Component({
  selector: 'app-table-header',
  imports: [MatButton, MatIcon, TranslatePipe],
  templateUrl: './table-header.html',
  styleUrl: './table-header.scss',
})
export class TableHeader {
  private readonly dialog = inject(MatDialog);

  selectedExperimentId = input<number | undefined>(undefined);

  onCreate(): void {
    this.dialog.open(ExperimentEdit, { width: '60vw', maxWidth: '1200px', autoFocus: true });
  }

  onEdit(): void {
    const experimentId = 1;
    if (experimentId === undefined) return;

    this.dialog.open(ExperimentEdit, {
      width: '60vw',
      maxWidth: '1200px',
      autoFocus: true,
      bindings: [inputBinding('experimentId', () => experimentId)],
    });
  }

  onDownload(): void {
    // Not implemented yet.
  }
}
