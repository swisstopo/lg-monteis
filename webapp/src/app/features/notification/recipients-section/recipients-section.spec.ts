import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MatChipHarness } from '@angular/material/chips/testing';
import { MatSelectHarness } from '@angular/material/select/testing';
import {
  DirectoryUserDto,
  RecipientControllerService,
  RecipientsResponseDto,
} from '@core/generated';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { RecipientsSection } from './recipients-section';

const PI_ANNA: DirectoryUserDto = {
  id: 'anna',
  firstName: 'Anna',
  lastName: 'Arnold',
  email: 'anna@example.com',
};
const PI_BEN: DirectoryUserDto = { id: 'ben', firstName: 'Ben', lastName: 'Berger' };
const READER_CARL: DirectoryUserDto = {
  id: 'carl',
  firstName: 'Carl',
  lastName: 'Christen',
  email: 'carl@example.com',
};

function recipients(saved: boolean, ...selected: DirectoryUserDto[]): RecipientsResponseDto {
  const candidates = [PI_ANNA, PI_BEN, READER_CARL].map((user) => ({
    user,
    selected: selected.includes(user),
  }));
  return { saved, candidates };
}

describe('RecipientsSection', () => {
  let recipientApi: {
    findRecipientsOf: ReturnType<typeof vi.fn>;
    replaceRecipientsOf: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    recipientApi = {
      findRecipientsOf: vi.fn(() => of(recipients(false, PI_ANNA))),
      replaceRecipientsOf: vi.fn(() => of(recipients(true))),
    };
  });

  async function render(): Promise<SectionView> {
    TestBed.configureTestingModule({
      imports: [RecipientsSection],
      providers: [
        provideTranslateService(),
        { provide: RecipientControllerService, useValue: recipientApi },
      ],
    });
    const fixture = TestBed.createComponent(RecipientsSection);
    fixture.componentRef.setInput('experimentId', 'x');
    await fixture.whenStable();
    return new SectionView(fixture);
  }

  it('offers the candidates of the experiment that are no recipient yet', async () => {
    const section = await render();

    expect(await section.offeredCandidates()).toEqual([
      'Ben Berger',
      'Carl Christen (carl@example.com)',
    ]);
  });

  it('adds a picked candidate as chip and no longer offers it', async () => {
    const section = await render();

    await section.addCandidate('Carl Christen');

    expect(await section.chips()).toEqual(['Anna Arnold', 'Carl Christen']);
    expect(await section.offeredCandidates()).toEqual(['Ben Berger']);
  });

  it('removes any recipient, a pi too, and offers it again', async () => {
    recipientApi.findRecipientsOf.mockReturnValue(of(recipients(false, PI_ANNA, PI_BEN)));
    const section = await render();

    await section.removeChip('Anna Arnold');

    expect(await section.chips()).toEqual(['Ben Berger']);
    expect(await section.offeredCandidates()).toEqual([
      'Anna Arnold (anna@example.com)',
      'Carl Christen (carl@example.com)',
    ]);
  });

  it('saves the preselected pis of a never saved list', async () => {
    recipientApi.findRecipientsOf.mockReturnValue(of(recipients(false, PI_ANNA, PI_BEN)));
    const section = await render();

    await section.save();

    expect(recipientApi.replaceRecipientsOf).toHaveBeenCalledWith('x', {
      userIds: ['anna', 'ben'],
    });
  });

  it('saves the full edited list and shows the stored one', async () => {
    recipientApi.findRecipientsOf.mockReturnValue(of(recipients(false, PI_ANNA, PI_BEN)));
    recipientApi.replaceRecipientsOf.mockReturnValue(of(recipients(true, PI_BEN, READER_CARL)));
    const section = await render();
    await section.removeChip('Anna Arnold');
    await section.addCandidate('Carl Christen');

    await section.save();

    expect(recipientApi.replaceRecipientsOf).toHaveBeenCalledWith('x', {
      userIds: ['ben', 'carl'],
    });
    expect(await section.chips()).toEqual(['Ben Berger', 'Carl Christen']);
  });

  it('says nobody gets the alarms for a saved empty list', async () => {
    recipientApi.findRecipientsOf.mockReturnValue(of(recipients(true)));

    const section = await render();

    expect(section.text()).toContain('notification.recipients.empty');
  });

  it('says nobody gets the alarms once the last recipient is removed', async () => {
    const section = await render();
    expect(section.text()).not.toContain('notification.recipients.empty');

    await section.removeChip('Anna Arnold');

    expect(section.text()).toContain('notification.recipients.empty');
  });

  it('offers no editing while the recipients cannot be loaded', async () => {
    recipientApi.findRecipientsOf.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404 })),
    );

    const section = await render();

    expect(await section.canEdit()).toBe(false);
  });

  it('says why the recipients cannot be edited when keycloak is down', async () => {
    recipientApi.findRecipientsOf.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 503 })),
    );

    const section = await render();

    expect(section.error()).toBe('error.user-directory.unavailable');
  });

  it('tells a keycloak that refuses the lookup apart from one that is down', async () => {
    recipientApi.findRecipientsOf.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 502,
            error: { messageKey: 'error.user-directory.denied', target: 'GLOBAL' },
          }),
      ),
    );

    const section = await render();

    expect(section.error()).toBe('error.user-directory.denied');
  });
});

class SectionView {
  private readonly loader;
  private readonly element: HTMLElement;

  constructor(fixture: ComponentFixture<RecipientsSection>) {
    this.loader = TestbedHarnessEnvironment.loader(fixture);
    this.element = fixture.nativeElement as HTMLElement;
  }

  async offeredCandidates(): Promise<string[]> {
    const select = await this.loader.getHarness(MatSelectHarness);
    await select.open();
    const options = await select.getOptions();
    const texts = await Promise.all(options.map((option) => option.getText()));
    await select.close();
    return texts.map((text) => text.replace(/\s+/g, ' '));
  }

  async addCandidate(name: string): Promise<void> {
    const select = await this.loader.getHarness(MatSelectHarness);
    await select.open();
    await select.clickOptions({ text: new RegExp(`^${name}`) });
  }

  async chips(): Promise<string[]> {
    const chips = await this.loader.getAllHarnesses(MatChipHarness);
    return Promise.all(chips.map((chip) => chip.getText()));
  }

  async removeChip(text: string): Promise<void> {
    const chip = await this.loader.getHarness(MatChipHarness.with({ text }));
    await chip.remove();
  }

  async save(): Promise<void> {
    const button = await this.loader.getHarness(MatButtonHarness.with({ text: /save/i }));
    await button.click();
  }

  async canEdit(): Promise<boolean> {
    const selects = await this.loader.getAllHarnesses(MatSelectHarness);
    const saveButtons = await this.loader.getAllHarnesses(MatButtonHarness.with({ text: /save/i }));
    return selects.length > 0 || saveButtons.length > 0;
  }

  error(): string | undefined {
    return this.element.querySelector('[data-testid="recipients-error"]')?.textContent?.trim();
  }

  text(): string {
    return this.element.textContent ?? '';
  }
}
