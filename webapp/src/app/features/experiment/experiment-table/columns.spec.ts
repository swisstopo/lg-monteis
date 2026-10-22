import { DatePipe } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { OwnersCellRenderer } from '@features/experiment/owners/owners-cell-renderer';
import { ExperimentService } from '@features/experiment/services/experiment.service';
import { ALICE, BOB } from '@features/experiment/testing/experiment-fixtures';
import { provideTranslateService } from '@ngx-translate/core';
import { ValueFormatterParams } from 'ag-grid-community';
import { beforeEach, describe, expect, it, Mocked, vi } from 'vitest';
import { createColumns } from './columns';

describe('experiment columns', () => {
  let experimentService: Mocked<Pick<ExperimentService, 'findAllVisibleOwners'>>;

  beforeEach(() => {
    experimentService = { findAllVisibleOwners: vi.fn().mockResolvedValue([ALICE, BOB]) };
    TestBed.configureTestingModule({
      providers: [
        provideTranslateService(),
        { provide: ExperimentService, useValue: experimentService },
      ],
    });
  });

  function ownerColumn() {
    const columns = TestBed.runInInjectionContext(() =>
      createColumns(new DatePipe('en-US'), (id) => id),
    );
    return columns.find((column) => column.field === 'owners')!;
  }

  it('cannot sort by owner, the names live in Keycloak', () => {
    const column = ownerColumn();

    expect(column.sortable).toBe(false);
    expect(column.cellRenderer).toBe(OwnersCellRenderer);
  });

  it('filters by the visible owners, by name but on their id', async () => {
    const values = await ownerColumn().filterParams.valuesProvider();

    expect(values).toEqual([
      { displayName: 'Alice Muster', value: 'alice' },
      { displayName: 'Bob Beispiel', value: 'bob' },
    ]);
  });

  it('offers no owner as a filter value', () => {
    expect(ownerColumn().filterParams.blankOptionLabel).toBe('experiment.owners.none');
  });

  it('exports the owner names separated by commas', () => {
    const format = ownerColumn().valueFormatter as (params: ValueFormatterParams) => string;

    expect(format({ value: [ALICE, BOB] } as ValueFormatterParams)).toBe(
      'Alice Muster, Bob Beispiel',
    );
    expect(format({ value: undefined } as ValueFormatterParams)).toBe('');
  });
});
