import { TestBed } from '@angular/core/testing';
import { FieldTree } from '@angular/forms/signals';
import { TranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorDto } from '../../core/generated';
import { FormErrorService } from './form-error.service';
import { ToastService } from './toast.service';

function mockField(): FieldTree<unknown> {
  return {} as unknown as FieldTree<unknown>;
}

describe('FormErrorService', () => {
  let service: FormErrorService;
  let toastService: ToastService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        FormErrorService,
        ToastService,
        {
          provide: TranslateService,
          useValue: {
            instant: vi.fn((key: string) => `translated:${key}`),
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
