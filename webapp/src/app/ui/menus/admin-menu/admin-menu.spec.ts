import { Component, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { RouteButton } from '@ui/buttons/route-button/route-button';
import { describe, expect, it } from 'vitest';
import AdminMenu from './admin-menu';

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

describe('AdminMenu', () => {
  it('links to the organisation table', async () => {
    TestBed.configureTestingModule({
      imports: [AdminMenu],
      providers: [provideTranslateService()],
    });
    TestBed.overrideComponent(AdminMenu, {
      remove: { imports: [RouteButton] },
      add: { imports: [RouteButtonStub] },
    });
    const fixture = TestBed.createComponent(AdminMenu);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    const entry = element.querySelector('[data-testid="admin-menu-organisation-entry"]');
    expect(entry?.textContent).toContain('/organisation-table');
  });
});
