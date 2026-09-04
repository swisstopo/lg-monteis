import { Component, effect, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { WorkbenchView } from '@scion/workbench';
import { Giro3d } from '../../../ui/giro3d/giro3d';

@Component({
  standalone: true,
  imports: [Giro3d],
  selector: 'app-measurements-visualization',
  styleUrl: './measurements-visualization.scss',
  templateUrl: './measurements-visualization.html',
})
export default class MeasurementsVisualization {
  private readonly translate = inject(TranslateService);
  private readonly view = inject(WorkbenchView);

  protected readonly tilesetUrl =
    'https://3d.oslandia.com/3dtiles/19_rue_Marc_Antoine_Petit_ifc/tileset.json';

  constructor() {
    effect(() => {
      this.view.title = this.translate.translate('tab.measurements-visualization')();
    });
  }
}
