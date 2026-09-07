import { Component, effect, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { WorkbenchView } from '@scion/workbench';
import { OAuthService } from 'angular-oauth2-oidc';
import { TILESET_URL_PREFIX, tilesetAuthorization } from '../../../core/auth/tileset-auth';
import { Giro3d } from '../../../ui/giro3d/giro3d';

@Component({
  imports: [Giro3d],
  selector: 'app-measurements-visualization',
  styleUrl: './measurements-visualization.scss',
  templateUrl: './measurements-visualization.html',
})
export default class MeasurementsVisualization {
  private readonly translate = inject(TranslateService);
  private readonly view = inject(WorkbenchView);
  private readonly oauthService = inject(OAuthService);

  // protected readonly tilesetUrl = `${TILESET_URL_PREFIX}/example/tileset.json`;
  protected readonly tilesetUrl = `${TILESET_URL_PREFIX}/monteis-octree-poc/tileset.json`;
  protected readonly authorization = tilesetAuthorization(this.oauthService);

  constructor() {
    effect(() => {
      this.view.title = this.translate.translate('tab.measurements-visualization')();
    });
  }
}
