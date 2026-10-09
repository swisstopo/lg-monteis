import { TestBed } from '@angular/core/testing';
import { ExperimentOwnerDto, ExperimentResponseDto } from '@core/generated';
import { provideTranslateService } from '@ngx-translate/core';
import { ICellRendererParams } from 'ag-grid-community';
import { beforeEach, describe, expect, it } from 'vitest';
import { OwnersCellRenderer } from './owners-cell-renderer';
import { OwnersStatus } from './owners-status';

const ALICE: ExperimentOwnerDto = {
  id: 'alice',
  firstName: 'Alice',
  lastName: 'Muster',
  email: 'alice@example.com',
};
const BOB: ExperimentOwnerDto = { id: 'bob', firstName: 'Bob', lastName: 'Beispiel' };

function params(owners: ExperimentOwnerDto[], ownersStatus: OwnersStatus) {
  return {
    value: owners,
    data: { owners, ownersStatus },
  } as ICellRendererParams<ExperimentResponseDto, ExperimentOwnerDto[]>;
}

describe('OwnersCellRenderer', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [OwnersCellRenderer],
      providers: [provideTranslateService()],
    });
  });

  async function render(owners: ExperimentOwnerDto[], status: OwnersStatus) {
    const fixture = TestBed.createComponent(OwnersCellRenderer);
    fixture.componentInstance.agInit(params(owners, status));
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    return { fixture, element };
  }

  it('lists the owner names separated by commas', async () => {
    const { element } = await render([ALICE, BOB], 'SHOWN');

    expect(element.textContent?.replace(/\s+/g, ' ').trim()).toBe('Alice Muster, Bob Beispiel');
    expect(element.querySelectorAll('[data-testid="experiment-owner"]')).toHaveLength(2);
  });

  it('shows a dash when the owners are missing', async () => {
    const { element } = await render([], 'ACCESS_DENIED');

    expect(element.querySelector('[data-testid="owners-missing"]')?.textContent).toBe('–');
    expect(element.querySelector('[data-testid="experiment-owner"]')).toBeNull();
  });

  it('shows the new owners on refresh', async () => {
    const { fixture, element } = await render([ALICE], 'SHOWN');

    expect(fixture.componentInstance.refresh(params([BOB], 'SHOWN'))).toBe(true);
    await fixture.whenStable();

    expect(element.textContent?.trim()).toBe('Bob Beispiel');
  });
});
