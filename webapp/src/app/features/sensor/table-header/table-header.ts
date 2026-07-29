import { Component, inject, input } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { MatFormField, MatInput } from '@angular/material/input';
import SensorCreate from '../sensor-create/sensor-create';

@Component({
  selector: 'app-table-header',
  imports: [MatButton, MatIcon, MatFormField, MatInput],
  templateUrl: './table-header.html',
  styleUrl: './table-header.scss',
})
export class TableHeader {
  private readonly dialog = inject(MatDialog);

  selectedSensorId = input<number | undefined>(undefined);

  onCreate(): void {
    this.dialog.open(SensorCreate, { width: '60vw', maxWidth: '900px', autoFocus: false });
  }

  onEdit(): void {
    const sensorId = this.selectedSensorId();
    if (sensorId === undefined) return;

    this.dialog.open(SensorCreate, {
      width: '60vw',
      maxWidth: '900px',
      autoFocus: false,
    });
    console.log(sensorId);
  }

  onDownload(): void {
    // Not implemented yet.
  }
}
