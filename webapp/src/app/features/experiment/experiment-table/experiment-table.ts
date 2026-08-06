import { Component, effect, signal } from '@angular/core';
import { WorkbenchView } from '@scion/workbench';
import { TableHeader } from '../../../shared/table-header/table-header';
import ExperimentEdit from '../experiment-edit/experiment-edit';

@Component({
  selector: 'app-sensor-table',
  imports: [TableHeader, TableHeader],
  templateUrl: './experiment-table.html',
  styleUrl: './experiment-table.scss',
})
export default class ExperimentTable {
  protected selectedExperimentId = signal<number | undefined>(1);
  protected readonly ExperimentEdit = ExperimentEdit;

  constructor(view: WorkbenchView) {
    // SCION Workbench: Dynamically update the tab title whenever the data changes
    effect(() => {});
  }
}
