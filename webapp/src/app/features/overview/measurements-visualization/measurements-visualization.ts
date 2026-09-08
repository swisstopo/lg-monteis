import { Component, effect, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { WorkbenchView } from '@scion/workbench';
import { Giro3d } from '../../../ui/giro3d/giro3d';
import { TilesFetch } from '../../../ui/giro3d/tiles-fetch-plugin';
import { MeasurementsTiles, TILESET_URL_PREFIX } from '../services/measurements-tiles';

@Component({
  imports: [Giro3d],
  selector: 'app-measurements-visualization',
  styleUrl: './measurements-visualization.scss',
  templateUrl: './measurements-visualization.html',
})
export default class MeasurementsVisualization {
  private readonly translate = inject(TranslateService);
  private readonly view = inject(WorkbenchView);
  private readonly tiles = inject(MeasurementsTiles);

  // protected readonly tilesetUrl = `${TILESET_URL_PREFIX}/example/tileset.json`;
  protected readonly tilesetUrl = `${TILESET_URL_PREFIX}/monteis-octree-poc/tileset.json`;
  protected readonly fetchTiles: TilesFetch = (url, options) => this.tiles.fetch(url, options);

  constructor() {
    effect(() => {
      this.view.title = this.translate.translate('tab.measurements-visualization')();
    });
  }
}
