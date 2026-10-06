import { Component, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { describe, expect, it } from 'vitest';
import { DialogLayout, DialogMode } from './dialog-layout';

@Component({
  imports: [DialogLayout],
  template: `
    <app-dialog-layout title="Edit Experiment" [mode]="mode()">
      <p>the form</p>
      <button dialog-action-dismiss>Cancel</button>
      <ng-container ngProjectAs="[dialog-action-submit]">
        <button>Save and create new</button>
        <button>Save</button>
      </ng-container>
    </app-dialog-layout>
  `,
})
class LayoutHost {
  readonly mode = input<DialogMode>('edit');
}

describe('DialogLayout', () => {
  async function render(mode: DialogMode): Promise<HTMLElement> {
    TestBed.configureTestingModule({
      imports: [LayoutHost],
      providers: [{ provide: MatDialogRef, useValue: { close: () => undefined } }],
    });
    const fixture = TestBed.createComponent(LayoutHost);
    fixture.componentRef.setInput('mode', mode);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  function actions(element: HTMLElement): (string | null)[] {
    const container = element.querySelector('mat-dialog-actions')!;
    return [...container.querySelectorAll('button')].map((button) => button.textContent);
  }

  it('shows the title as the dialog title and the content in the dialog content', async () => {
    const element = await render('edit');

    expect(element.querySelector('[mat-dialog-title]')?.textContent).toBe('Edit Experiment');
    expect(element.querySelector('mat-dialog-content')?.textContent).toContain('the form');
  });

  it('shows dismiss and then submit actions, right aligned, when creating or editing', async () => {
    for (const mode of ['create', 'edit'] as const) {
      TestBed.resetTestingModule();
      const element = await render(mode);

      expect(actions(element)).toEqual(['Cancel', 'Save and create new', 'Save']);
      expect(element.querySelector('mat-dialog-actions')?.getAttribute('align')).toBe('end');
    }
  });

  it('shows only the dismiss action in view mode, there is nothing to submit', async () => {
    const element = await render('view');

    expect(actions(element)).toEqual(['Cancel']);
  });
});
