import { TestBed } from '@angular/core/testing';
import { ExperimentOwnerDto } from '@core/generated';
import { ExperimentService } from '@features/experiment/services/experiment.service';
import { provideTranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ExperimentOwnerPicker } from './experiment-owner-picker';

const ALICE: ExperimentOwnerDto = {
  id: 'alice',
  firstName: 'Alice',
  lastName: 'Muster',
  email: 'alice@example.com',
};
const BOB: ExperimentOwnerDto = {
  id: 'bob',
  firstName: 'Bob',
  lastName: 'Beispiel',
  email: 'bob@example.com',
};

describe('ExperimentOwnerPicker', () => {
  let experimentService: { getOwnerCandidates: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    experimentService = { getOwnerCandidates: vi.fn().mockResolvedValue([ALICE, BOB]) };
    TestBed.configureTestingModule({
      imports: [ExperimentOwnerPicker],
      providers: [
        provideTranslateService(),
        { provide: ExperimentService, useValue: experimentService },
      ],
    });
  });

  async function render(selectedOwnerIds: string[] = [], error?: string) {
    const fixture = TestBed.createComponent(ExperimentOwnerPicker);
    fixture.componentRef.setInput('experimentId', 'experiment-1');
    fixture.componentRef.setInput('selectedOwnerIds', selectedOwnerIds);
    fixture.componentRef.setInput('error', error);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    const input = () => element.querySelector<HTMLInputElement>('[data-testid="owner-input"]')!;
    return {
      fixture,
      element,
      input,
      chips: () =>
        [...element.querySelectorAll('[data-testid="selected-owner"]')].map((chip) =>
          chip.textContent?.replace('cancel', '').trim(),
        ),
      hint: () => element.querySelector('mat-hint')?.textContent?.trim(),
      async open() {
        input().focus();
        await fixture.whenStable();
      },
      async type(text: string) {
        input().value = text;
        input().dispatchEvent(new Event('input'));
        await fixture.whenStable();
      },
      options: () =>
        [...document.querySelectorAll<HTMLElement>('mat-option')].map((option) =>
          option.textContent?.trim().replace(/\s+/g, ' '),
        ),
      async pick(name: string) {
        [...document.querySelectorAll<HTMLElement>('mat-option')]
          .find((option) => option.textContent?.includes(name))!
          .click();
        await fixture.whenStable();
      },
    };
  }

  it('loads the candidates of the experiment', async () => {
    await render();

    expect(experimentService.getOwnerCandidates).toHaveBeenCalledWith('experiment-1');
  });

  it('shows the selected owners as chips', async () => {
    const view = await render(['alice']);

    expect(view.chips()).toEqual(['Alice Muster']);
  });

  it('says who can be an owner', async () => {
    const view = await render();

    expect(view.hint()).toBe('experiment.owners.hint');
  });

  it('offers the candidates that are not selected yet with their email', async () => {
    const view = await render(['alice']);

    await view.open();

    expect(view.options()).toEqual(['Bob Beispiel (bob@example.com)']);
  });

  it('offers a candidate without email by name only', async () => {
    experimentService.getOwnerCandidates.mockResolvedValue([{ ...ALICE, email: undefined }]);
    const view = await render();

    await view.open();

    expect(view.options()).toEqual(['Alice Muster']);
  });

  it('filters the candidates by name or email', async () => {
    const view = await render();
    await view.open();

    await view.type('beisp');
    expect(view.options()).toEqual(['Bob Beispiel (bob@example.com)']);

    await view.type('ALICE@');
    expect(view.options()).toEqual(['Alice Muster (alice@example.com)']);
  });

  it('adds a picked candidate and clears the search', async () => {
    const view = await render(['alice']);
    await view.open();
    await view.type('bob');

    await view.pick('Bob Beispiel');

    expect(view.fixture.componentInstance.selectedOwnerIds()).toEqual(['alice', 'bob']);
    expect(view.chips()).toEqual(['Alice Muster', 'Bob Beispiel']);
    expect(view.input().value).toBe('');
  });

  it('removes an owner from its chip', async () => {
    const view = await render(['alice', 'bob']);

    view.element.querySelector<HTMLButtonElement>('[matChipRemove]')!.click();
    await view.fixture.whenStable();

    expect(view.fixture.componentInstance.selectedOwnerIds()).toEqual(['bob']);
  });

  it('leaves out selected owners who are no candidates anymore', async () => {
    const view = await render(['alice', 'former']);

    expect(view.chips()).toEqual(['Alice Muster']);
  });

  it('says so when nobody can be an owner', async () => {
    experimentService.getOwnerCandidates.mockResolvedValue([]);

    const view = await render();

    expect(view.hint()).toBe('experiment.owners.noCandidates');
  });

  it('disables the search when the candidates cannot be loaded', async () => {
    experimentService.getOwnerCandidates.mockRejectedValue(new Error('503'));

    const view = await render();

    expect(view.input().disabled).toBe(true);
    expect(view.hint()).toBe('error.user-directory.unavailable');
  });

  it('shows the error of a failed save instead of the hint', async () => {
    const view = await render([], 'error.user-directory.unavailable');

    expect(view.element.querySelector('[data-testid="owner-error"]')?.textContent?.trim()).toBe(
      'error.user-directory.unavailable',
    );
  });
});
