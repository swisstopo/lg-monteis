import { DatePipe } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { ExperimentControllerService } from '@core/generated';
import { provideTranslateService } from '@ngx-translate/core';
import { ValueFormatterParams } from 'ag-grid-community';
import { beforeEach, describe, expect, it } from 'vitest';
import { createColumns } from './columns';

describe('sensor columns', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideTranslateService(),
        { provide: ExperimentControllerService, useValue: {} },
      ],
    });
  });

  function lastFulcrumRefreshColumn() {
    const columns = TestBed.runInInjectionContext(() =>
      createColumns(new DatePipe('en-US', 'UTC')),
    );
    return columns.find((column) => column.field === 'lastFulcrumRefreshAt')!;
  }

  it('sorts by the last Fulcrum refresh but offers no filter', () => {
    const column = lastFulcrumRefreshColumn();

    expect(column.sortable).toBe(true);
    expect(column.filter).toBe(false);
  });

  it('shows the last Fulcrum refresh to the minute and nothing without one', () => {
    const format = lastFulcrumRefreshColumn().valueFormatter as (
      params: ValueFormatterParams,
    ) => string;

    expect(format({ value: '2026-11-02T10:05:30Z' } as ValueFormatterParams)).toBe(
      '02.11.2026 10:05',
    );
    expect(format({ value: undefined } as ValueFormatterParams)).toBe('');
  });
});
