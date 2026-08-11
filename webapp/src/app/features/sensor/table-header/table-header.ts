import { DatePipe } from '@angular/common';
import { Component, inject, input, inputBinding } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { MatFormField, MatInput } from '@angular/material/input';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { PermissionsService } from '../../../core/auth/permissions.service';
import SensorEdit from '../sensor-edit/sensor-edit';

@Component({
  selector: 'app-table-header',
  imports: [MatButton, MatIcon, MatFormField, MatInput, TranslatePipe],
  providers: [DatePipe],
  templateUrl: './table-header.html',
  styleUrl: './table-header.scss',
})
export class TableHeader {
  private readonly dialog = inject(MatDialog);
  protected readonly permissions = inject(PermissionsService);
  private readonly datePipe = inject(DatePipe);
  private readonly i18nService = inject(TranslateService);

  selectedSensorId = input<number | undefined>(undefined);

  onCreate(): void {
    this.dialog.open(SensorEdit, { width: '60vw', maxWidth: '1200px', autoFocus: true });
  }

  onEdit(): void {
    const sensorId = this.selectedSensorId();
    if (sensorId === undefined) return;

    this.dialog.open(SensorEdit, {
      width: '60vw',
      maxWidth: '1200px',
      autoFocus: true,
      bindings: [inputBinding('sensorId', () => sensorId)],
    });
  }

  onDownload(): void {
    // Not implemented yet.
  }
}
