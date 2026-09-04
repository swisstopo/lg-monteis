import HttpConfiguration from '@giro3d/giro3d/utils/HttpConfiguration.js';
import { OAuthService } from 'angular-oauth2-oidc';
import { filter } from 'rxjs';

export const TILESET_URL_PREFIX = `${window.location.origin}/api/tilesets`;

/**
 * Set the bearer token whenever it changes to send it with Giro3d's tile
 * requests.
 */
export function syncTilesetAuthorization(oauthService: OAuthService): void {
  const applyAccessToken = () => {
    const accessToken = oauthService.getAccessToken();
    if (accessToken) {
      HttpConfiguration.setAuth(TILESET_URL_PREFIX, `Bearer ${accessToken}`);
    }
  };

  applyAccessToken();
  oauthService.events
    .pipe(filter((event) => event.type === 'token_received'))
    .subscribe(applyAccessToken);
}
