import { TestBed } from '@angular/core/testing';
import { FulcrumDocumentFileName } from '@features/fulcrum-document/fulcrum-document-file-name/fulcrum-document-file-name';
import { provideTranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { createColumns } from './columns';

describe('fulcrum document columns', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideTranslateService()] });
  });

  it('shows the file name, then number, title, authors, year and category', () => {
    const columns = TestBed.runInInjectionContext(() => createColumns());

    expect(columns.map((column) => column.field)).toEqual([
      'file.fileName',
      'number',
      'title',
      'authors',
      'year',
      'category',
    ]);
    expect(columns[0].cellRenderer).toBe(FulcrumDocumentFileName);
  });
});
