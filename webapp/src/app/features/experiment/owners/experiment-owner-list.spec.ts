import { TestBed } from '@angular/core/testing';
import { ExperimentOwnerDto } from '@core/generated';
import { provideTranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { ExperimentOwnerList } from './experiment-owner-list';
import { OwnersStatus } from './owners-status';

const ALICE: ExperimentOwnerDto = {
  id: 'alice',
  firstName: 'Alice',
  lastName: 'Muster',
  email: 'alice@example.com',
};

describe('ExperimentOwnerList', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ExperimentOwnerList],
      providers: [provideTranslateService()],
    });
  });

  async function render(owners: ExperimentOwnerDto[], status?: OwnersStatus) {
    const fixture = TestBed.createComponent(ExperimentOwnerList);
    fixture.componentRef.setInput('owners', owners);
    fixture.componentRef.setInput('status', status);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('lists each owner with a mailto link', async () => {
    const element = await render([ALICE], 'SHOWN');

    const owner = element.querySelector('[data-testid="experiment-owner"]')!;
    expect(owner.textContent).toContain('Alice Muster');
    expect(owner.querySelector('a')?.getAttribute('href')).toBe('mailto:alice@example.com');
  });

  it('shows an owner without email by name only', async () => {
    const element = await render([{ ...ALICE, email: undefined }], 'SHOWN');

    const owner = element.querySelector('[data-testid="experiment-owner"]')!;
    expect(owner.textContent?.trim()).toBe('Alice Muster');
    expect(owner.querySelector('a')).toBeNull();
  });

  it('says so when there is no owner', async () => {
    const element = await render([], 'SHOWN');

    expect(element.querySelector('[data-testid="no-owners"]')?.textContent).toContain(
      'experiment.owners.none',
    );
  });

  it('says why the owners are missing instead of listing them', async () => {
    const element = await render([], 'KEYCLOAK_UNAVAILABLE');

    expect(element.querySelector('[data-testid="owners-missing"]')?.textContent).toContain(
      'experiment.owners.status.KEYCLOAK_UNAVAILABLE',
    );
    expect(element.querySelector('[data-testid="no-owners"]')).toBeNull();
  });
});
