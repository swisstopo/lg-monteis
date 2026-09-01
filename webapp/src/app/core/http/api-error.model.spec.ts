import { HttpErrorResponse } from '@angular/common/http';
import { ErrorDto } from '@core/generated';
import { describe, expect, it } from 'vitest';
import { AppErrorResponse, toErrorDtos } from './api-error.model';

function httpError(status: number, error?: unknown): HttpErrorResponse {
  return new HttpErrorResponse({ status, error });
}

describe('toErrorDtos', () => {
  it('returns the body as-is when it is an array', () => {
    const dtos: ErrorDto[] = [{ messageKey: 'a' }, { messageKey: 'b' }];
    expect(toErrorDtos(httpError(400, dtos))).toEqual(dtos);
  });

  it('wraps a single object body into a one-element array', () => {
    const dto: ErrorDto = { messageKey: 'a' };
    expect(toErrorDtos(httpError(400, dto))).toEqual([dto]);
  });

  it('returns an empty array for a null/string body', () => {
    expect(toErrorDtos(httpError(500, null))).toEqual([]);
    expect(toErrorDtos(httpError(502, '<html>Bad Gateway</html>'))).toEqual([]);
  });
});

describe('AppErrorResponse', () => {
  it('exposes the underlying status', () => {
    expect(new AppErrorResponse(httpError(418)).status).toBe(418);
  });

  it('isUnauthorized is true only on 401', () => {
    expect(new AppErrorResponse(httpError(401)).isUnauthorized()).toBe(true);
    expect(new AppErrorResponse(httpError(403)).isUnauthorized()).toBe(false);
  });

  it('isForbidden is true only on 403', () => {
    expect(new AppErrorResponse(httpError(403)).isForbidden()).toBe(true);
    expect(new AppErrorResponse(httpError(401)).isForbidden()).toBe(false);
  });

  it('isServerError is true on 0 and >= 500, false otherwise', () => {
    expect(new AppErrorResponse(httpError(0)).isServerError()).toBe(true);
    expect(new AppErrorResponse(httpError(500)).isServerError()).toBe(true);
    expect(new AppErrorResponse(httpError(503)).isServerError()).toBe(true);
    expect(new AppErrorResponse(httpError(400)).isServerError()).toBe(false);
    expect(new AppErrorResponse(httpError(403)).isServerError()).toBe(false);
  });

  describe('dtosTargetGlobalOrUndefined', () => {
    it('keeps GLOBAL and undefined targets, drops FORM/FIELD', () => {
      const body: ErrorDto[] = [
        { target: ErrorDto.TargetEnum.Global, messageKey: 'global' },
        { messageKey: 'no-target' },
        { target: ErrorDto.TargetEnum.Form, messageKey: 'form' },
        { target: ErrorDto.TargetEnum.Field, field: 'name', messageKey: 'field' },
      ];

      expect(new AppErrorResponse(httpError(400, body)).dtosTargetGlobalOrUndefined()).toEqual([
        { target: ErrorDto.TargetEnum.Global, messageKey: 'global' },
        { messageKey: 'no-target' },
      ]);
    });

    it('handles a single-object body', () => {
      const body: ErrorDto = { target: ErrorDto.TargetEnum.Global, messageKey: 'global' };
      expect(new AppErrorResponse(httpError(400, body)).dtosTargetGlobalOrUndefined()).toEqual([
        body,
      ]);
    });

    it('returns an empty array for a null body', () => {
      expect(new AppErrorResponse(httpError(500, null)).dtosTargetGlobalOrUndefined()).toEqual([]);
    });
  });
});
