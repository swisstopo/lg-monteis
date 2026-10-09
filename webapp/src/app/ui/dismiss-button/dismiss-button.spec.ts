import { TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { provideTranslateService } from '@ngx-translate/core';
import { describe, expect, it, vi } from 'vitest';
import { DismissButton } from './dismiss-button';

async function render(kind: 'cancel' | 'close', dialogRef?: { close: () => void }) {
  TestBed.configureTestingModule({
    imports: [DismissButton],
    providers: [
      provideTranslateService(),
      ...(dialogRef ? [{ provide: MatDialogRef, useValue: dialogRef }] : []),
    ],
  });
  const fixture = TestBed.createComponent(DismissButton);
  fixture.componentRef.setInput('kind', kind);
  const dismissed = vi.fn();
  fixture.componentInstance.dismissed.subscribe(dismissed);
  await fixture.whenStable();
  const button = (fixture.nativeElement as HTMLElement).querySelector('button')!;
  return { button, dismissed };
}

describe('DismissButton', () => {
  it('is a quiet cancel text button', async () => {
    const { button } = await render('cancel');

    expect(button.textContent?.trim()).toBe('button.cancel');
    expect(button.classList).not.toContain('mat-mdc-unelevated-button');
  });

  it('is a filled close button', async () => {
    const { button } = await render('close');

    expect(button.textContent?.trim()).toBe('button.close');
    expect(button.classList).toContain('mat-mdc-unelevated-button');
  });

  it('closes the dialog it is in and emits dismissed', async () => {
    const dialogRef = { close: vi.fn() };
    const { button, dismissed } = await render('cancel', dialogRef);

    button.click();

    expect(dialogRef.close).toHaveBeenCalled();
    expect(dismissed).toHaveBeenCalled();
  });

  it('only emits dismissed outside a dialog', async () => {
    const { button, dismissed } = await render('close');

    button.click();

    expect(dismissed).toHaveBeenCalled();
  });
});
