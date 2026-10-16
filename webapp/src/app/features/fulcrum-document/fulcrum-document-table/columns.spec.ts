import { TestBed } from '@angular/core/testing';
import { FulcrumDocumentActions } from '@features/fulcrum-document/fulcrum-document-actions/fulcrum-document-actions';
import { provideTranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { createColumns } from './columns';

describe('fulcrum document columns', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideTranslateService()] });
  });

  it('shows number, title, authors, year and category, then the file actions', () => {
    const columns = TestBed.runInInjectionContext(() => createColumns());

    expect(columns.map((column) => column.field ?? column.colId)).toEqual([
      'number',
      'title',
      'authors',
      'year',
      'category',
      'file',
    ]);
    expect(columns.at(-1)?.cellRenderer).toBe(FulcrumDocumentActions);
  });
});
