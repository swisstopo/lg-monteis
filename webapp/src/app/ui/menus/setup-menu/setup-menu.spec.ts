import { Component, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { RouteButton } from '@ui/buttons/route-button/route-button';
import { describe, expect, it } from 'vitest';
import SetupMenu from './setup-menu';

/** Stands in for RouteButton, whose workbench router link needs a running SCION workbench. */
@Component({
  selector: 'app-route-button',
  template: '{{ route().join("/") }}',
})
class RouteButtonStub {
  route = input.required<string[]>();
  icon = input<string | null>(null);
  label = input.required<string>();
}

async function render(): Promise<HTMLElement> {
  TestBed.configureTestingModule({
    imports: [SetupMenu],
    providers: [provideTranslateService()],
  });
  TestBed.overrideComponent(SetupMenu, {
    remove: { imports: [RouteButton] },
    add: { imports: [RouteButtonStub] },
  });

  const fixture = TestBed.createComponent(SetupMenu);
  await fixture.whenStable();
  return fixture.nativeElement as HTMLElement;
}

// Sensor reads are open to every user (row-level security filters them), so both entries are
// always shown; the sensor write actions are gated inside the table instead.
describe('SetupMenu', () => {
  it('shows the Sensor entry', async () => {
    const element = await render();

    const sensorEntry = element.querySelector('[data-testid="setup-menu-sensor-entry"]');
    expect(sensorEntry?.textContent).toContain('/sensor-table');
  });

  it('shows the Experiment entry', async () => {
    const element = await render();

    const experimentEntry = element.querySelector('[data-testid="setup-menu-experiment-entry"]');
    expect(experimentEntry?.textContent).toContain('/experiment-table');
  });
});
