import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { form, required } from '@angular/forms/signals';
import { describe, expect, it, vi } from 'vitest';
import { SubmitButton } from './submit-button';

@Component({
  imports: [SubmitButton],
  template: `
    <form id="nameForm" (submit)="$event.preventDefault(); submitted = true"></form>
    <app-submit-button [form]="nameForm" formId="nameForm">Save</app-submit-button>
    <app-submit-button [form]="nameForm" variant="secondary" (pressed)="pressed()">
      Save and create new
    </app-submit-button>
  `,
})
class SubmitHost {
  readonly model = signal({ name: '' });
  readonly nameForm = form(this.model, (path) => required(path.name));
  submitted = false;
  readonly pressed = vi.fn();
}

async function render() {
  const fixture = TestBed.createComponent(SubmitHost);
  await fixture.whenStable();
  const buttons = [...(fixture.nativeElement as HTMLElement).querySelectorAll('button')];
  return { fixture, save: buttons[0], saveAndCreateNew: buttons[1] };
}

describe('SubmitButton', () => {
  it('is disabled while the form is invalid', async () => {
    const { save, saveAndCreateNew } = await render();

    expect(save.disabled).toBe(true);
    expect(saveAndCreateNew.disabled).toBe(true);
  });

  it('submits the form it names', async () => {
    const view = await render();
    view.fixture.componentInstance.model.set({ name: 'Mont Terri' });
    await view.fixture.whenStable();

    view.save.click();

    expect(view.save.type).toBe('submit');
    expect(view.save.getAttribute('form')).toBe('nameForm');
    expect(view.fixture.componentInstance.submitted).toBe(true);
  });

  it('only emits pressed without a form to submit', async () => {
    const view = await render();
    view.fixture.componentInstance.model.set({ name: 'Mont Terri' });
    await view.fixture.whenStable();

    view.saveAndCreateNew.click();

    expect(view.saveAndCreateNew.type).toBe('button');
    expect(view.fixture.componentInstance.pressed).toHaveBeenCalled();
    expect(view.fixture.componentInstance.submitted).toBe(false);
  });

  it('is filled as the primary action and tonal as a secondary one', async () => {
    const { save, saveAndCreateNew } = await render();

    expect(save.classList).toContain('mat-mdc-unelevated-button');
    expect(saveAndCreateNew.classList).toContain('mat-tonal-button');
  });
});
