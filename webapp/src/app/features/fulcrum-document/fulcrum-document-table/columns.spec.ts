import { TestBed } from '@angular/core/testing';
import { FulcrumDocumentFiles } from '@features/fulcrum-document/fulcrum-document-files/fulcrum-document-files';
import { provideTranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { createColumns } from './columns';

describe('fulcrum document columns', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideTranslateService()] });
  });

  it('shows number, title, authors, year and category, then the files', () => {
    const columns = TestBed.runInInjectionContext(() => createColumns());

    expect(columns.map((column) => column.field ?? column.colId)).toEqual([
      'number',
      'title',
      'authors',
      'year',
      'category',
      'files',
    ]);
    expect(columns.at(-1)?.cellRenderer).toBe(FulcrumDocumentFiles);
  });
});
