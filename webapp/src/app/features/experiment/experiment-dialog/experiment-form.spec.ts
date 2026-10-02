import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { form } from '@angular/forms/signals';
import { ExperimentResponseDto } from '@core/generated';
import { provideTranslateService, TranslateService } from '@ngx-translate/core';
import { describe, expect, it } from 'vitest';
import { ExperimentFormModel, experimentSchema, toFormModel, toWriteDto } from './experiment-form';

const EXPERIMENT: ExperimentResponseDto = {
  id: 'experiment-1',
  version: 3,
  name: 'Mont Terri Alpha',
  comment: 'borehole',
  period: { start: '2030-01-01', end: '2030-05-05' },
};

function validModel(): ExperimentFormModel {
  return {
    name: 'Mont Terri Alpha',
    comment: '',
    period: { start: new Date(2030, 0, 1), end: new Date(2030, 4, 5) },
  };
}

/** The form over `model`, built in an injection context like a component field initializer. */
function experimentForm(model: ExperimentFormModel) {
  TestBed.configureTestingModule({ providers: [provideTranslateService()] });
  return TestBed.runInInjectionContext(() =>
    form(signal(model), experimentSchema(TestBed.inject(TranslateService))),
  );
}

describe('toFormModel', () => {
  it('maps an experiment', () => {
    expect(toFormModel(EXPERIMENT)).toEqual({
      name: 'Mont Terri Alpha',
      comment: 'borehole',
      period: { start: new Date('2030-01-01'), end: new Date('2030-05-05') },
    });
  });

  it('starts a new experiment empty, running from today', () => {
    const model = toFormModel(undefined);

    expect(model.name).toBe('');
    expect(model.comment).toBe('');
    expect(model.period.start.toDateString()).toBe(new Date().toDateString());
    expect(model.period.end.toDateString()).toBe(new Date().toDateString());
  });
});

describe('toWriteDto', () => {
  it('updates the given experiment with its id and version', () => {
    expect(toWriteDto({ ...validModel(), comment: 'note' }, EXPERIMENT)).toEqual({
      id: 'experiment-1',
      version: 3,
      name: 'Mont Terri Alpha',
      comment: 'note',
      period: { start: '2030-01-01', end: '2030-05-05' },
    });
  });

  it('creates an experiment without id and version', () => {
    const dto = toWriteDto(validModel(), undefined);

    expect(dto.id).toBeUndefined();
    expect(dto.version).toBeUndefined();
  });

  it('drops a blank comment', () => {
    expect(toWriteDto({ ...validModel(), comment: '  ' }, undefined).comment).toBeUndefined();
  });

  it('trims the comment', () => {
    expect(toWriteDto({ ...validModel(), comment: ' note ' }, undefined).comment).toBe('note');
  });
});

describe('experimentSchema', () => {
  function errorsOf(model: ExperimentFormModel) {
    const field = experimentForm(model);
    return {
      name: field
        .name()
        .errors()
        .map((error) => error.message),
      comment: field
        .comment()
        .errors()
        .map((error) => error.message),
      start: field.period
        .start()
        .errors()
        .map((error) => error.message),
      end: field.period
        .end()
        .errors()
        .map((error) => error.message),
      valid: field().valid(),
    };
  }

  it('accepts a valid experiment', () => {
    expect(errorsOf(validModel()).valid).toBe(true);
  });

  it('requires a name', () => {
    expect(errorsOf({ ...validModel(), name: '' }).name).toContain(
      'experiment.name.validation.required',
    );
  });

  it('rejects a name shorter than 2 characters', () => {
    expect(errorsOf({ ...validModel(), name: 'x' }).name).toEqual([
      'experiment.name.validation.minLength',
    ]);
  });

  it('rejects a name longer than 50 characters', () => {
    expect(errorsOf({ ...validModel(), name: 'x'.repeat(51) }).name).toEqual([
      'experiment.name.validation.maxLength',
    ]);
  });

  it('rejects a comment longer than 4096 characters', () => {
    expect(errorsOf({ ...validModel(), comment: 'x'.repeat(4097) }).comment).toEqual([
      'experiment.comment.validation.maxLength',
    ]);
  });

  it('accepts a period of a single day', () => {
    const day = new Date(2030, 0, 1);

    expect(errorsOf({ ...validModel(), period: { start: day, end: day } }).valid).toBe(true);
  });

  it('rejects an end before the start on both dates', () => {
    const errors = errorsOf({
      ...validModel(),
      period: { start: new Date(2030, 4, 5), end: new Date(2030, 0, 1) },
    });

    expect(errors.start).toEqual(['experiment.experimentDate.from.validation.bounds']);
    expect(errors.end).toEqual(['experiment.experimentDate.to.validation.bounds']);
  });
});
