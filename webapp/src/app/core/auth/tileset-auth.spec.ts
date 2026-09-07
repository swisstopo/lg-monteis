import { OAuthService } from 'angular-oauth2-oidc';
import { describe, expect, it } from 'vitest';
import { tilesetAuthorization } from './tileset-auth';

const oauthServiceStub = (accessToken: string | null) => {
  const stub = { accessToken, getAccessToken: () => stub.accessToken };
  return stub as typeof stub & OAuthService;
};

describe('tilesetAuthorization', () => {
  it('provides the current access token as a bearer token', () => {
    expect(tilesetAuthorization(oauthServiceStub('a-token'))()).toBe('Bearer a-token');
  });

  it('provides no authorization while there is no access token', () => {
    expect(tilesetAuthorization(oauthServiceStub(null))()).toBeNull();
  });

  it('reads the access token on every call, so a refreshed token is picked up', () => {
    const oauthService = oauthServiceStub('initial-token');
    const authorization = tilesetAuthorization(oauthService);

    expect(authorization()).toBe('Bearer initial-token');

    oauthService.accessToken = 'refreshed-token';

    expect(authorization()).toBe('Bearer refreshed-token');
  });
});
