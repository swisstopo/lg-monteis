import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { TestBed } from '@angular/core/testing';
import { ExperimentOwnerDto } from '@core/generated';
import { ALICE } from '@features/experiment/testing/experiment-fixtures';
import { ExperimentOwnerListHarness } from '@features/experiment/testing/experiment-owner-list.harness';
import { provideTranslateService } from '@ngx-translate/core';
import { describe, expect, it } from 'vitest';
import { ExperimentOwnerList } from './experiment-owner-list';
import { OwnersStatus } from './owners-status';

async function setup(owners: ExperimentOwnerDto[], status: OwnersStatus = 'SHOWN') {
  TestBed.configureTestingModule({
    imports: [ExperimentOwnerList],
    providers: [provideTranslateService()],
  });
  const fixture = TestBed.createComponent(ExperimentOwnerList);
  fixture.componentRef.setInput('owners', owners);
  fixture.componentRef.setInput('status', status);
  return TestbedHarnessEnvironment.harnessForFixture(fixture, ExperimentOwnerListHarness);
}

describe('ExperimentOwnerList', () => {
  it('lists each owner with a mailto link', async () => {
    const list = await setup([ALICE]);

    const [owner] = await list.getOwners();
    expect(await owner.getName()).toBe('Alice Muster');
    expect(await owner.getMailto()).toBe('mailto:alice@example.com');
  });

  it('shows an owner without email by name only', async () => {
    const list = await setup([{ ...ALICE, email: undefined }]);

    const [owner] = await list.getOwners();
    expect(await owner.getName()).toBe('Alice Muster');
    expect(await owner.getMailto()).toBeNull();
  });

  it('says so when there is no owner', async () => {
    const list = await setup([]);

    expect(await list.saysNoOwner()).toBe(true);
  });

  it('says why the owners are missing instead of listing them', async () => {
    const list = await setup([], 'KEYCLOAK_UNAVAILABLE');

    expect(await list.getMissingReason()).toBe('experiment.owners.status.KEYCLOAK_UNAVAILABLE');
    expect(await list.saysNoOwner()).toBe(false);
  });
});
