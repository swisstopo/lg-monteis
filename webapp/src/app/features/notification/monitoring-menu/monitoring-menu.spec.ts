import { Component, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ExperimentControllerService, ExperimentResponseDto } from '@core/generated';
import { provideTranslateService, TranslateService } from '@ngx-translate/core';
import translationsEN from '@public/i18n/en.json';
import { RouteButton } from '@ui/buttons/route-button/route-button';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import MonitoringMenu from './monitoring-menu';

// RouteButton's workbench router link needs a running SCION workbench
@Component({
  selector: 'app-route-button',
  template: '{{ route().join("/") }} {{ label() }}',
})
class RouteButtonStub {
  route = input.required<string[]>();
  icon = input<string | null>(null);
  label = input.required<string>();
}

async function render(experiments: Partial<ExperimentResponseDto>[]) {
  const api = { getAllExperiments: vi.fn(() => of(experiments)) };
  TestBed.configureTestingModule({
    imports: [MonitoringMenu],
    providers: [
      provideTranslateService({ lang: 'en' }),
      { provide: ExperimentControllerService, useValue: api },
    ],
  });
  TestBed.overrideComponent(MonitoringMenu, {
    remove: { imports: [RouteButton] },
    add: { imports: [RouteButtonStub] },
  });

  TestBed.inject(TranslateService).setTranslation('en', translationsEN);

  const fixture = TestBed.createComponent(MonitoringMenu);
  await fixture.whenStable();
  fixture.detectChanges();
  return { element: fixture.nativeElement as HTMLElement, api };
}

describe('MonitoringMenu', () => {
  it('lists the experiments the user may write', async () => {
    const { api } = await render([]);

    expect(api.getAllExperiments).toHaveBeenCalledWith('WRITE');
  });

  it('shows one notifications entry per experiment, in the order they come', async () => {
    const { element } = await render([
      { id: 'b', name: 'Bentonite' },
      { id: 'g', name: 'Gas Path' },
    ]);

    const entries = [
      ...element.querySelectorAll('[data-testid="monitoring-menu-notifications-entry"]'),
    ].map((entry) => entry.textContent?.trim());
    expect(entries).toEqual([
      '/monitoring/b Notifications – Bentonite',
      '/monitoring/g Notifications – Gas Path',
    ]);
  });
});
