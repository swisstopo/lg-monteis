import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MultiSelectFilter } from './multi-select-filter';

function flushPromises(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

describe('MultiSelectFilter', () => {
  let component: MultiSelectFilter;
  let translateSpy: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    translateSpy = vi.fn((key: string) => signal(`translated:${key}`));

    await TestBed.configureTestingModule({
      imports: [MultiSelectFilter],
      providers: [{ provide: TranslateService, useValue: { translate: translateSpy } }],
    }).compileComponents();

    component = TestBed.createComponent(MultiSelectFilter).componentInstance;
  });

  async function initWithOptions(
    options: { displayName: string; value: string | null }[],
    blankOptionLabel?: string,
  ): Promise<void> {
    component.agInit({
      valuesProvider: () => Promise.resolve(options),
      blankOptionLabel,
    } as Parameters<MultiSelectFilter['agInit']>[0]);
    await flushPromises();
  }

  describe('getModelAsString', () => {
    it('returns an empty string for a null model', () => {
      expect(component.getModelAsString(null)).toBe('');
    });

    it('returns the display name for a single selected value', async () => {
      await initWithOptions([
        { displayName: 'DAS-01', value: 'das-01' },
        { displayName: 'DAS-02', value: 'das-02' },
      ]);

      expect(component.getModelAsString({ filterType: 'set', values: ['das-01'] })).toBe('DAS-01');
    });

    it('shows a translated count summary for multiple selected values', async () => {
      await initWithOptions([
        { displayName: 'DAS-01', value: 'das-01' },
        { displayName: 'DAS-02', value: 'das-02' },
      ]);

      const result = component.getModelAsString({
        filterType: 'set',
        values: ['das-01', 'das-02'],
      });

      expect(translateSpy).toHaveBeenCalledWith('common.multiSelectFilter.floatingFilterSummary', {
        count: 2,
      });
      expect(result).toBe('translated:common.multiSelectFilter.floatingFilterSummary');
    });

    it('resolves the blank pseudo-option to its label', async () => {
      await initWithOptions([{ displayName: 'Alpha', value: 'alpha' }], 'No Main Experiment');

      expect(component.getModelAsString({ filterType: 'set', values: [null] })).toBe(
        'No Main Experiment',
      );
    });

    it('falls back to the raw value for a selection no longer present in the current options', async () => {
      await initWithOptions([{ displayName: 'DAS-01', value: 'das-01' }]);

      expect(component.getModelAsString({ filterType: 'set', values: ['das-99'] })).toBe('das-99');
    });
  });
});
