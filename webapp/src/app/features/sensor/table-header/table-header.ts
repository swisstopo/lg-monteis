import { DatePipe } from '@angular/common';
import { Component, inject, input, inputBinding, outputBinding } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { MatFormField, MatInput } from '@angular/material/input';
import { PermissionsService } from '../../../core/auth/permissions.service';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import Chart from '../../../ui/chart/chart';
import {
  generateMockHumidityDataset,
  generateMockPressureDataset,
  generateMockStressRadialDataset,
  generateMockTemperatureDataset,
} from '../../../ui/chart/chart-data-mock';
import { ChartDataset, ChartOptions } from '../../../ui/chart/chart.types';
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

  protected onPlot() {
    const rangeFrom = this.datePipe.transform('2026-05-20');
    const rangeTo = this.datePipe.transform('2026-06-24');
    const datasets: ChartDataset[] = [
      generateMockPressureDataset(),
      generateMockStressRadialDataset(),
      generateMockTemperatureDataset(),
      generateMockHumidityDataset(),
    ];
    const title = this.i18nService.translate('chart.title', {
      name: 'MyFancyExperiment',
      rangeFrom: rangeFrom,
      rangeTo: rangeTo,
    })();
    const options: ChartOptions = {
      title: title,
      subtitle: 'Plot of 666 measurements',
      xAxisType: 'time',
      xAxisLabel: 'Date',
      yAxisLabels: {
        y: 'Fluid Pressure [kPa]',
        y2: 'Radial Stress [bar]',
        y3: 'Temperature [°C]',
        y4: 'Relative Humidity [%]',
      },
      advancedOptions: {
        plugins: {
          zoom: {
            zoom: {
              wheel: {
                enabled: true, // Enables zooming with the mouse wheel
              },
              pinch: {
                enabled: false,
              },
              drag: {
                enabled: true,
                backgroundColor: 'rgba(66, 133, 244, 0.2)', // Optional: Customize the rectangle's color
                borderWidth: 1, // Optional: Outline the rectangle
                borderColor: 'rgba(66, 133, 244, 1)',
              },
              mode: 'xy', // Allow zooming on the x-axis only (can be 'y' or 'xy')
            },
          },
        },
      },
    };

    this.dialog.open(Chart, {
      width: '95vw',
      maxWidth: '100%',
      height: '95vh',
      maxHeight: '100%',
      autoFocus: true,
      bindings: [
        inputBinding('title', () => title),
        inputBinding('datasets', () => datasets),
        inputBinding('options', () => options),
        outputBinding('pointClick', (event) => console.log('click: ', event)),
        outputBinding('pointHover', (event) => console.log('hover: ', event)),
      ],
    });
  }
}
