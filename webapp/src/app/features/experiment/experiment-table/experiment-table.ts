import { Component, effect, inject, inputBinding, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { TranslatePipe } from '@ngx-translate/core';
import { WorkbenchView } from '@scion/workbench';
import { TableHeader } from '../../../shared/table-header/table-header';
import ExperimentEdit from '../../experiment/experiment-edit/experiment-edit';

@Component({
  selector: 'app-experiment-table',
  imports: [TableHeader, TableHeader, MatButton, MatIcon, TranslatePipe],
  templateUrl: './experiment-table.html',
  styleUrl: './experiment-table.scss',
})
export default class ExperimentTable {
  private readonly dialog = inject(MatDialog);

  protected selectedExperimentId = signal<number | undefined>(1);
  readonly searchTerm = signal<string>('');

  constructor(view: WorkbenchView) {
    // SCION Workbench: Dynamically update the tab title whenever the data changes
    effect(() => {});
  }

  onCreate(): void {
    this.dialog.open(ExperimentEdit, { width: '60vw', maxWidth: '1200px', autoFocus: true });
  }

  onEdit(): void {
    const experimentId = this.selectedExperimentId();
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

  onSearch(term: string): void {
    this.searchTerm.set(term);
  }
}
