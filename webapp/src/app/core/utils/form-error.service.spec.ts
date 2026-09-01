import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FieldTree } from '@angular/forms/signals';
import { TranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorDto } from '../generated';
import { ToastService } from '../notifications/toast.service';
import { FormErrorService } from './form-error.service';

function mockField(): FieldTree<unknown> {
  return {} as unknown as FieldTree<unknown>;
}

describe('FormErrorService', () => {
  let service: FormErrorService;
  let toastService: ToastService;
  let translateSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    translateSpy = vi.fn((key: string) => signal(`translated:${key}`));

    TestBed.configureTestingModule({
      providers: [
        FormErrorService,
        ToastService,
        {
          provide: TranslateService,
          useValue: {
            translate: translateSpy,
          },
        },
      ],
    });
    service = TestBed.inject(FormErrorService);
    toastService = TestBed.inject(ToastService);
  });

  it('returns undefined when errors is undefined', () => {
    expect(service.mapApiErrorsToFormErrors(undefined, {} as FieldTree<unknown>)).toBeUndefined();
  });

  it('returns an empty array when errors is empty', () => {
    expect(service.mapApiErrorsToFormErrors([], {} as FieldTree<unknown>)).toEqual([]);
  });

  it('maps a field error to a serverError', () => {
    const nameField = mockField();
    const form = { name: nameField };
    const errors: ErrorDto[] = [{ field: 'name', messageKey: 'validation.required' }];

    const result = service.mapApiErrorsToFormErrors(errors, form as FieldTree<unknown>);

    expect(result).toEqual([
      {
        kind: 'serverError',
        message: 'translated:validation.required',
        fieldTree: nameField,
      },
    ]);
  });

  it('maps a nested field error to a serverError', () => {
    const xField = mockField();
    const form = { coordinates: { x: xField } };
    const errors: ErrorDto[] = [{ field: 'coordinates.x', messageKey: 'validation.required' }];

    const result = service.mapApiErrorsToFormErrors(errors, form as unknown as FieldTree<unknown>);

    expect(result).toEqual([
      {
        kind: 'serverError',
        message: 'translated:validation.required',
        fieldTree: xField,
      },
    ]);
  });

  it('toasts unmapped errors and excludes them from returned errors', () => {
    const toastSpy = vi.spyOn(toastService, 'error');
    const form = {} as FieldTree<unknown>;
    const errors: ErrorDto[] = [{ messageKey: 'validation.required' }];

    const result = service.mapApiErrorsToFormErrors(errors, form);

    expect(result).toEqual([]);
    expect(toastSpy).toHaveBeenCalledWith('translated:validation.required');
  });

  it('forwards the error params to translate for unmapped errors', () => {
    const form = {} as FieldTree<unknown>;
    const errors: ErrorDto[] = [{ messageKey: 'validation.tooLong', params: { max: 10 } }];

    service.mapApiErrorsToFormErrors(errors, form);

    expect(translateSpy).toHaveBeenCalledWith('validation.tooLong', { max: 10 });
  });

  it('uses the fallback message key when messageKey is missing', () => {
    const nameField = mockField();
    const form = { name: nameField };
    const errors: ErrorDto[] = [{ field: 'name' }];

    const result = service.mapApiErrorsToFormErrors(
      errors,
      form as FieldTree<unknown>,
      'custom.fallback',
    );

    expect(result).toEqual([
      {
        kind: 'serverError',
        message: 'translated:custom.fallback',
        fieldTree: nameField,
      },
    ]);
  });

  it('uses the default fallback key when no fallback is provided', () => {
    const toastSpy = vi.spyOn(toastService, 'error');
    const form = {} as FieldTree<unknown>;
    const errors: ErrorDto[] = [{}];

    const result = service.mapApiErrorsToFormErrors(errors, form);

    expect(result).toEqual([]);
    expect(toastSpy).toHaveBeenCalledWith('translated:error.system.generic');
  });
});
