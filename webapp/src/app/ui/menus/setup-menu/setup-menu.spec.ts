import { Component, input, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { PermissionsService } from '@core/auth/permissions.service';
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

async function render(isAdmin: boolean): Promise<HTMLElement> {
  TestBed.configureTestingModule({
    imports: [SetupMenu],
    providers: [
      provideTranslateService(),
      { provide: PermissionsService, useValue: { isAdmin: signal(isAdmin) } },
    ],
  });
  TestBed.overrideComponent(SetupMenu, {
    remove: { imports: [RouteButton] },
    add: { imports: [RouteButtonStub] },
  });

  const fixture = TestBed.createComponent(SetupMenu);
  await fixture.whenStable();
  return fixture.nativeElement as HTMLElement;
}

describe('SetupMenu', () => {
  it('shows the Sensor entry to an admin', async () => {
    const element = await render(true);

    const sensorEntry = element.querySelector('[data-testid="setup-menu-sensor-entry"]');
    expect(sensorEntry).not.toBeNull();
    expect(sensorEntry?.textContent).toContain('/sensor-table');
  });

  it('hides the Sensor entry from a non-admin', async () => {
    const element = await render(false);

    expect(element.querySelector('[data-testid="setup-menu-sensor-entry"]')).toBeNull();
    expect(element.textContent).not.toContain('/sensor-table');
  });

  it.each([true, false])('always shows the Experiment entry (isAdmin: %s)', async (isAdmin) => {
    const element = await render(isAdmin);

    const experimentEntry = element.querySelector('[data-testid="setup-menu-experiment-entry"]');
    expect(experimentEntry?.textContent).toContain('/experiment-table');
  });
});
