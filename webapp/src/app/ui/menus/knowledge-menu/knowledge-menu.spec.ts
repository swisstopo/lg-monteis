import { Component, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { RouteButton } from '@ui/buttons/route-button/route-button';
import { describe, expect, it } from 'vitest';
import KnowledgeMenu from './knowledge-menu';

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
    imports: [KnowledgeMenu],
    providers: [provideTranslateService()],
  });
  TestBed.overrideComponent(KnowledgeMenu, {
    remove: { imports: [RouteButton] },
    add: { imports: [RouteButtonStub] },
  });

  const fixture = TestBed.createComponent(KnowledgeMenu);
  await fixture.whenStable();
  return fixture.nativeElement as HTMLElement;
}

// Every user sees the list of Fulcrum Documents, only their files need document access.
describe('KnowledgeMenu', () => {
  it('shows the Fulcrum Documents entry', async () => {
    const element = await render();

    const entry = element.querySelector('[data-testid="knowledge-menu-fulcrum-document-entry"]');
    expect(entry?.textContent).toContain('/fulcrum-document-table');
  });
});
