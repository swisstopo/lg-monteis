import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ErrorDto } from '@core/generated';
import { ToastService } from '@core/notifications/toast.service';
import { TranslateService } from '@ngx-translate/core';
import { OAuthService } from 'angular-oauth2-oidc';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { restErrorInterceptor } from './rest-error.interceptors';

describe('restErrorInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;
  let toastService: { error: ReturnType<typeof vi.fn> };
  let oauthService: { initLoginFlow: ReturnType<typeof vi.fn> };
  let translateService: { translate: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    toastService = { error: vi.fn() };
    oauthService = { initLoginFlow: vi.fn() };
    translateService = { translate: vi.fn((key: string) => signal(key)) };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([restErrorInterceptor])),
        provideHttpClientTesting(),
        { provide: ToastService, useValue: toastService },
        { provide: OAuthService, useValue: oauthService },
        { provide: TranslateService, useValue: translateService },
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

  it('toasts a GLOBAL error and forwards its params to translate', () => {
    const body: ErrorDto[] = [
      { target: ErrorDto.TargetEnum.Global, messageKey: 'error.foo', params: { count: 3 } },
    ];

    httpClient.get('/api/whatever').subscribe({ error: () => {} });
    httpMock.expectOne('/api/whatever').flush(body, { status: 400, statusText: 'Bad Request' });

    expect(translateService.translate).toHaveBeenCalledWith('error.foo', { count: 3 });
    expect(toastService.error).toHaveBeenCalledWith('error.foo');
  });

  it('falls back to error.system.internal when a GLOBAL error has no messageKey', () => {
    const body: ErrorDto[] = [{ target: ErrorDto.TargetEnum.Global }];

    httpClient.get('/api/whatever').subscribe({ error: () => {} });
    httpMock.expectOne('/api/whatever').flush(body, { status: 400, statusText: 'Bad Request' });

    expect(toastService.error).toHaveBeenCalledWith('error.system.internal');
  });

  it('toasts an error with an undefined target', () => {
    const body: ErrorDto[] = [{ messageKey: 'error.foo' }];

    httpClient.get('/api/whatever').subscribe({ error: () => {} });
    httpMock.expectOne('/api/whatever').flush(body, { status: 400, statusText: 'Bad Request' });

    expect(toastService.error).toHaveBeenCalledWith('error.foo');
  });

  it('does not toast when a 400 carries only non-global (FORM/FIELD) errors', () => {
    const body: ErrorDto[] = [
      { target: ErrorDto.TargetEnum.Form, messageKey: 'error.form' },
      { target: ErrorDto.TargetEnum.Field, field: 'name', messageKey: 'error.field' },
    ];

    httpClient.get('/api/whatever').subscribe({ error: () => {} });
    httpMock.expectOne('/api/whatever').flush(body, { status: 400, statusText: 'Bad Request' });

    expect(toastService.error).not.toHaveBeenCalled();
  });
});
