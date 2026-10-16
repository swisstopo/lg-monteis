import { TestBed } from '@angular/core/testing';
import { FulcrumEntryDocuments } from '@features/fulcrum-document/fulcrum-entry-documents/fulcrum-entry-documents';
import { provideTranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { createColumns } from './columns';

describe('fulcrum entry columns', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideTranslateService()] });
  });

  it('shows number, title, authors, year and category, then the documents', () => {
    const columns = TestBed.runInInjectionContext(() => createColumns());

    expect(columns.map((column) => column.field ?? column.colId)).toEqual([
      'number',
      'title',
      'authors',
      'year',
      'category',
      'documents',
    ]);
    expect(columns.at(-1)?.cellRenderer).toBe(FulcrumEntryDocuments);
  });
});
