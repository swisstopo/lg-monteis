import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { OAuthService } from 'angular-oauth2-oidc';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastService } from '../notifications/toast.service';
import { restErrorInterceptor } from './rest-error.interceptors';

describe('restErrorInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;
  let toastService: { error: ReturnType<typeof vi.fn> };
  let oauthService: { initLoginFlow: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    toastService = { error: vi.fn() };
    oauthService = { initLoginFlow: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([restErrorInterceptor])),
        provideHttpClientTesting(),
        { provide: ToastService, useValue: toastService },
        { provide: OAuthService, useValue: oauthService },
        { provide: TranslateService, useValue: { translate: (key: string) => signal(key) } },
      ],
    });

    httpClient = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('shows a session-expired toast with a re-login action on 401, without redirecting immediately', () => {
    httpClient.get('/api/whatever').subscribe({ error: () => {} });
    httpMock.expectOne('/api/whatever').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(toastService.error).toHaveBeenCalledWith(
      'error.auth.sessionExpired',
      undefined,
      expect.objectContaining({ label: 'error.auth.logInAgain' }),
    );
    expect(oauthService.initLoginFlow).not.toHaveBeenCalled();

    const action = toastService.error.mock.calls[0][2];
    action.onClick();

    expect(oauthService.initLoginFlow).toHaveBeenCalledOnce();
  });

  it('shows a plain no-permission toast on 403, without a re-login action', () => {
    httpClient.get('/api/whatever').subscribe({ error: () => {} });
    httpMock.expectOne('/api/whatever').flush(null, { status: 403, statusText: 'Forbidden' });

    expect(toastService.error).toHaveBeenCalledWith('error.auth.forbidden');
    expect(oauthService.initLoginFlow).not.toHaveBeenCalled();
  });

  it('still surfaces a generic toast for a 500 with no ErrorDto body', () => {
    httpClient.get('/api/whatever').subscribe({ error: () => {} });
    httpMock.expectOne('/api/whatever').flush(null, { status: 500, statusText: 'Server Error' });

    expect(toastService.error).toHaveBeenCalledWith('error.system.generic');
  });
});
