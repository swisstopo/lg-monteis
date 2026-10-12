import { manualChangeDetection } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ExperimentOwnerDto } from '@core/generated';
import { ExperimentService } from '@features/experiment/services/experiment.service';
import { ALICE, BOB } from '@features/experiment/testing/experiment-fixtures';
import { ExperimentOwnerPickerHarness } from '@features/experiment/testing/experiment-owner-picker.harness';
import { provideTranslateService } from '@ngx-translate/core';
import { describe, expect, it, Mocked, vi } from 'vitest';
import { ExperimentOwnerPicker } from './experiment-owner-picker';

const FRITZ: ExperimentOwnerDto = { id: 'fritz', firstName: 'Fritz', lastName: 'Former' };

interface Setup {
  selectedOwnerIds?: string[];
  owners?: ExperimentOwnerDto[];
  error?: string;
  candidates?: () => Promise<ExperimentOwnerDto[]>;
}

async function setup({
  selectedOwnerIds = [],
  owners = [],
  error,
  candidates = () => Promise.resolve([ALICE, BOB]),
}: Setup = {}) {
  const experimentService: Mocked<Pick<ExperimentService, 'findOwnerCandidatesForExperiment'>> = {
    findOwnerCandidatesForExperiment: vi.fn(candidates),
  };
  TestBed.configureTestingModule({
    imports: [ExperimentOwnerPicker],
    providers: [
      provideTranslateService(),
      { provide: ExperimentService, useValue: experimentService },
    ],
  });

  const fixture = TestBed.createComponent(ExperimentOwnerPicker);
  fixture.componentRef.setInput('experimentId', 'experiment-1');
  fixture.componentRef.setInput('selectedOwnerIds', selectedOwnerIds);
  fixture.componentRef.setInput('owners', owners);
  fixture.componentRef.setInput('error', error);
  const picker = await TestbedHarnessEnvironment.harnessForFixture(
    fixture,
    ExperimentOwnerPickerHarness,
  );
  const selectedOwnerIdsNow = () => fixture.componentInstance.selectedOwnerIds();

  return { fixture, picker, selectedOwnerIdsNow, experimentService };
}

describe('ExperimentOwnerPicker', () => {
  it('loads the candidates of the experiment', async () => {
    const { experimentService } = await setup();

    expect(experimentService.findOwnerCandidatesForExperiment).toHaveBeenCalledWith('experiment-1');
  });

  it('shows the names of the selected owners', async () => {
    const { picker } = await setup({ selectedOwnerIds: ['alice', 'bob'] });

    expect(await picker.getOwnerNames()).toEqual(['Alice Muster', 'Bob Beispiel']);
  });

  it('says who can be an owner', async () => {
    const { picker } = await setup();

    expect(await picker.getHint()).toBe('experiment.owners.hint');
  });

  it('offers every candidate with its email and checks the selected ones', async () => {
    const { picker } = await setup({ selectedOwnerIds: ['alice'] });

    expect(await picker.getOfferedOwners()).toEqual([
      { text: 'Alice Muster (alice@example.com)', selected: true, disabled: false },
      { text: 'Bob Beispiel (bob@example.com)', selected: false, disabled: false },
    ]);
  });

  it('offers a candidate without email by name only', async () => {
    const { picker } = await setup({
      candidates: () => Promise.resolve([{ ...ALICE, email: undefined }]),
    });

    expect((await picker.getOfferedOwners()).map((owner) => owner.text)).toEqual(['Alice Muster']);
  });

  it('adds a selected candidate', async () => {
    const { picker, selectedOwnerIdsNow } = await setup({ selectedOwnerIds: ['alice'] });

    await picker.selectOwner('Bob Beispiel');

    expect(selectedOwnerIdsNow()).toEqual(['alice', 'bob']);
    expect(await picker.getOwnerNames()).toEqual(['Alice Muster', 'Bob Beispiel']);
  });

  it('removes a deselected owner', async () => {
    const { picker, selectedOwnerIdsNow } = await setup({ selectedOwnerIds: ['alice', 'bob'] });

    await picker.deselectOwner('Alice Muster');

    expect(selectedOwnerIdsNow()).toEqual(['bob']);
  });

  it('leaves out selected ids it knows no name for', async () => {
    const { picker } = await setup({ selectedOwnerIds: ['alice', 'unknown'] });

    expect(await picker.getOwnerNames()).toEqual(['Alice Muster']);
  });

  it('shows a stored owner who is no candidate anymore unchecked and disabled', async () => {
    const { picker } = await setup({
      selectedOwnerIds: ['alice', 'fritz'],
      owners: [ALICE, FRITZ],
    });

    expect(await picker.getOwnerNames()).toEqual(['Alice Muster']);
    expect(await picker.getOfferedOwners()).toEqual([
      { text: 'Alice Muster (alice@example.com)', selected: true, disabled: false },
      { text: 'Bob Beispiel (bob@example.com)', selected: false, disabled: false },
      { text: 'Fritz Former – experiment.owners.formerPi', selected: false, disabled: true },
    ]);
  });

  it('drops a stored owner who is no candidate anymore once the selection changes', async () => {
    const { picker, selectedOwnerIdsNow } = await setup({
      selectedOwnerIds: ['alice', 'fritz'],
      owners: [ALICE, FRITZ],
    });

    await picker.selectOwner('Bob Beispiel');

    expect(selectedOwnerIdsNow()).toEqual(['alice', 'bob']);
  });

  // the pending load keeps the fixture from ever getting stable, the harness must not wait for it
  it('names the stored owners while the candidates load', () =>
    manualChangeDetection(async () => {
      const { fixture, picker } = await setup({
        selectedOwnerIds: ['alice'],
        owners: [ALICE],
        candidates: () => new Promise(() => undefined),
      });
      fixture.detectChanges();
      // mat-select applies its value only after its options are initialized
      await new Promise((resolve) => setTimeout(resolve));
      fixture.detectChanges();

      expect(await picker.isDisabled()).toBe(true);
      expect(await picker.getOwnerNames()).toEqual(['Alice Muster']);
    }));

  it('says so when nobody can be an owner', async () => {
    const { picker } = await setup({ candidates: () => Promise.resolve([]) });

    expect(await picker.getHint()).toBe('experiment.owners.noCandidates');
  });

  it('disables the select when the candidates cannot be loaded', async () => {
    const { picker } = await setup({ candidates: () => Promise.reject(new Error('503')) });

    expect(await picker.isDisabled()).toBe(true);
    expect(await picker.getHint()).toBe('error.user-directory.unavailable');
  });

  it('tells a Keycloak that refuses the lookup apart from one that is down', async () => {
    const refused = new HttpErrorResponse({
      status: 502,
      error: { messageKey: 'error.user-directory.denied', target: 'GLOBAL' },
    });
    const { picker } = await setup({ candidates: () => Promise.reject(refused) });

    expect(await picker.isDisabled()).toBe(true);
    expect(await picker.getHint()).toBe('error.user-directory.denied');
  });

  it('shows the error of a failed save instead of the hint', async () => {
    const { picker } = await setup({ error: 'experiment.owner.not-eligible' });

    expect(await picker.getHint()).toBe('experiment.owner.not-eligible');
  });
});
