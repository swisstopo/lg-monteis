import { Component, effect, inject, signal } from '@angular/core';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { WorkbenchView } from '@scion/workbench';
import { Giro3d } from '../../../ui/giro3d/giro3d';
import { TilesFetch } from '../../../ui/giro3d/tiles-fetch-plugin';
import { MeasurementsTiles, TILESET_URL_PREFIX } from '../services/measurements-tiles';

import { SensorControllerService, SensorResponseDto } from '../../../core/generated';
import { InlineError } from '../../../ui/inline-error/inline-error';

@Component({
  imports: [Giro3d, InlineError, TranslatePipe],
  selector: 'app-measurements-visualization',
  styleUrl: './measurements-visualization.scss',
  templateUrl: './measurements-visualization.html',
})
export default class MeasurementsVisualization {
  private readonly translate = inject(TranslateService);
  private readonly view = inject(WorkbenchView);
  private readonly tiles = inject(MeasurementsTiles);

  protected readonly tilesetUrl = `${TILESET_URL_PREFIX}/monteis-octree-poc/tileset.json`;
  protected readonly fetchTiles: TilesFetch = (url, options) => this.tiles.fetch(url, options);
  private readonly sensorControllerService = inject(SensorControllerService);

  protected readonly error = signal(false);
  protected sensors = signal<SensorResponseDto[]>([]);

  constructor() {
    effect(() => {
      this.view.title = this.translate.translate('tab.measurements-visualization')();
    });
  }

  ngOnInit() {
    const response = this.sensorControllerService.getSensors(0, 100);

    let sensors = [];
    response
      .forEach((results) => {
        if (results.rows) {
          for (let sensor of results.rows) {
            if (sensor.coordinates == null) {
              console.log(`Sensor ${sensor.name} has no coordinates, skipping.`);
            }
            sensors.push(sensor);
            this.sensors.set([...sensors]);
          }
        }
      })
      .catch((error) => {
        console.error('Sensor fetching issue', error);
        this.error.set(true);
      });
  }
}
