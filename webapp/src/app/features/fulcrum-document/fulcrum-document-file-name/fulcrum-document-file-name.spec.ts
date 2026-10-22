import { TestBed } from '@angular/core/testing';
import { FulcrumDocumentResponseDto } from '@core/generated';
import {
  fulcrumDocument,
  fulcrumDocumentFile,
} from '@features/fulcrum-document/testing/fulcrum-document-fixtures';
import { provideTranslateService } from '@ngx-translate/core';
import { ICellRendererParams } from 'ag-grid-community';
import { describe, expect, it } from 'vitest';
import { FulcrumDocumentFileName } from './fulcrum-document-file-name';

const REPORT = fulcrumDocument({ file: fulcrumDocumentFile({ fileName: 'report.pdf' }) });

describe('FulcrumDocumentFileName', () => {
  async function render(document: FulcrumDocumentResponseDto) {
    TestBed.configureTestingModule({
      imports: [FulcrumDocumentFileName],
      providers: [provideTranslateService()],
    });
    const fixture = TestBed.createComponent(FulcrumDocumentFileName);
    fixture.componentInstance.agInit({
      data: document,
    } as ICellRendererParams<FulcrumDocumentResponseDto>);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the file name', async () => {
    const element = await render(REPORT);

    expect(element.textContent).toContain('report.pdf');
  });

  it('marks a document removed from Fulcrum', async () => {
    const element = await render({ ...REPORT, availableInFulcrum: false });

    expect(
      element.querySelector('[data-testid="removed-from-fulcrum"]')?.getAttribute('aria-label'),
    ).toBe('fulcrumDocument.removed');
  });

  it('shows no marker while the document is in Fulcrum', async () => {
    const element = await render(REPORT);

    expect(element.querySelector('[data-testid="removed-from-fulcrum"]')).toBeNull();
  });
});
