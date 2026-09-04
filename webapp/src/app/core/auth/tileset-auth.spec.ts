import HttpConfiguration from '@giro3d/giro3d/utils/HttpConfiguration.js';
import { OAuthService } from 'angular-oauth2-oidc';
import { Subject } from 'rxjs';
import { afterEach, describe, expect, it } from 'vitest';
import { TILESET_URL_PREFIX, syncTilesetAuthorization } from './tileset-auth';

interface OAuthEventStub {
  type: string;
}

const oauthServiceStub = (accessToken: string | null) => {
  const events = new Subject<OAuthEventStub>();
  const stub = {
    events,
    accessToken,
    getAccessToken: () => stub.accessToken,
  };
  return stub as typeof stub & OAuthService;
};

const authorizationFor = (url: string) =>
  (HttpConfiguration.applyConfiguration(url)?.headers as Record<string, string> | undefined)?.[
    'Authorization'
  ];

describe('syncTilesetAuthorization', () => {
  // HttpConfiguration is module-global state, so it has to be reset between tests.
  afterEach(() => HttpConfiguration.clear());

  it('attaches the current access token to tileset requests', () => {
    syncTilesetAuthorization(oauthServiceStub('initial-token'));

    expect(authorizationFor(`${TILESET_URL_PREFIX}/example/tileset.json`)).toBe(
      'Bearer initial-token',
    );
  });

  it('attaches the token to nested tile requests as well', () => {
    syncTilesetAuthorization(oauthServiceStub('initial-token'));

    expect(authorizationFor(`${TILESET_URL_PREFIX}/example/ifc/13.b3dm`)).toBe(
      'Bearer initial-token',
    );
  });

  it('leaves requests outside the tileset prefix untouched', () => {
    syncTilesetAuthorization(oauthServiceStub('initial-token'));

    expect(authorizationFor(`${window.location.origin}/api/measurements`)).toBeUndefined();
  });

  it('registers nothing while there is no access token', () => {
    syncTilesetAuthorization(oauthServiceStub(null));

    expect(authorizationFor(`${TILESET_URL_PREFIX}/example/tileset.json`)).toBeUndefined();
  });

  it('updates the header when a refreshed token arrives', () => {
    const oauthService = oauthServiceStub('initial-token');
    syncTilesetAuthorization(oauthService);

    oauthService.accessToken = 'refreshed-token';
    oauthService.events.next({ type: 'token_received' });

    expect(authorizationFor(`${TILESET_URL_PREFIX}/example/tileset.json`)).toBe(
      'Bearer refreshed-token',
    );
  });

  it('ignores events that do not carry a new token', () => {
    const oauthService = oauthServiceStub('initial-token');
    syncTilesetAuthorization(oauthService);

    oauthService.accessToken = 'refreshed-token';
    oauthService.events.next({ type: 'token_expires' });

    expect(authorizationFor(`${TILESET_URL_PREFIX}/example/tileset.json`)).toBe(
      'Bearer initial-token',
    );
  });
});
