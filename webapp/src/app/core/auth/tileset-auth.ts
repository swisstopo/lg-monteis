import { OAuthService } from 'angular-oauth2-oidc';

export const TILESET_URL_PREFIX = `${window.location.origin}/api/tilesets`;

/**
 * Giro3d fetches the 3D tiles outside of `HttpClient`, so `authInterceptor`
 * never sees them. This supplies the token header, read fresh on every tile
 * request.
 */
export function tilesetAuthorization(oauthService: OAuthService): () => string | null {
  return () => {
    const accessToken = oauthService.getAccessToken();
    return accessToken ? `Bearer ${accessToken}` : null;
  };
}
