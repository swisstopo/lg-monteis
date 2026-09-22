import { Component, computed, effect, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { SensorControllerService, SensorParameterRowResponseDto } from '@core/generated';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { WorkbenchView } from '@scion/workbench';
import { Giro3d } from '@ui/giro3d/giro3d';
import { TilesFetch } from '@ui/giro3d/tiles-fetch-plugin';
import { InlineError } from '@ui/inline-error/inline-error';
import { MeasurementsTiles, TILESET_URL_PREFIX } from '../services/measurements-tiles';

/**
 * The scene shows every sensor at once, but `getSensors` is paged and returns one row per sensor
 * parameter - so ask for a page large enough to hold them all.
 */
const SENSOR_ROW_LIMIT = 500;

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
  private readonly api = inject(SensorControllerService);

  protected readonly tilesetUrl = `${TILESET_URL_PREFIX}/monteis-octree-poc/tileset.json`;
  protected readonly fetchTiles: TilesFetch = (url, options) => this.tiles.fetch(url, options);

  private readonly sensorRows = rxResource({
    stream: () => this.api.getSensors(0, SENSOR_ROW_LIMIT),
  });

  /**
   * `getSensors` is the sensor *table* endpoint: its rows carry `sensorId` instead of `id`, and
   * it returns one row per sensor parameter. Collapse those, or the scene stacks a sphere per
   * parameter on the very same spot.
   */
  protected readonly sensors = computed<SensorParameterRowResponseDto[]>(() => {
    const byId = new Map<string, SensorParameterRowResponseDto>();
    for (const row of this.sensorRows.value()?.rows ?? []) {
      if (row.sensorId === undefined || byId.has(row.sensorId)) continue;
      byId.set(row.sensorId, row);
    }
    return [...byId.values()];
  });

  protected readonly error = computed(() => this.sensorRows.error() !== undefined);

  constructor() {
    effect(() => {
      this.view.title = this.translate.translate('tab.measurements-visualization')();
    });
  }
}
