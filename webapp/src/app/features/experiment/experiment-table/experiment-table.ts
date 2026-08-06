import { Component, effect, signal } from '@angular/core';
import { WorkbenchView } from '@scion/workbench';
import { TableHeader } from '../table-header/table-header';

@Component({
  selector: 'app-sensor-table',
  imports: [TableHeader],
  templateUrl: './experiment-table.html',
  styleUrl: './experiment-table.scss',
})
export default class ExperimentTable {
  protected selectedExperimentId = signal<number | undefined>(undefined);

  constructor(view: WorkbenchView) {
    // SCION Workbench: Dynamically update the tab title whenever the data changes
    effect(() => {});
  }
}
