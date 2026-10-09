import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { form, FormField, required } from '@angular/forms/signals';
import { MatError, MatFormField, MatInput, MatLabel } from '@angular/material/input';
import { describe, expect, it } from 'vitest';
import { FieldErrors } from './field-errors';

@Component({
  imports: [FieldErrors, FormField, MatError, MatFormField, MatInput, MatLabel],
  template: `
    <mat-form-field>
      <mat-label>Name</mat-label>
      <input matInput [formField]="nameForm.name" />
      <mat-error [appFieldErrors]="nameForm.name" />
    </mat-form-field>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
class NameForm {
  readonly nameForm = form(signal({ name: '' }), (path) => {
    required(path.name, { message: 'Name is required' });
  });
}

async function render() {
  const fixture = TestBed.createComponent(NameForm);
  await fixture.whenStable();
  return fixture;
}

describe('FieldErrors', () => {
  it('shows nothing while the field is untouched', async () => {
    const fixture = await render();

    expect(fixture.nativeElement.querySelector('mat-error')).toBeNull();
  });

  it('shows the messages once the field is touched and invalid', async () => {
    const fixture = await render();

    fixture.componentInstance.nameForm.name().markAsTouched();
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('mat-error')?.textContent).toContain(
      'Name is required',
    );
  });

  it('hides the messages again once the field is valid', async () => {
    const fixture = await render();
    fixture.componentInstance.nameForm.name().markAsTouched();
    await fixture.whenStable();

    fixture.componentInstance.nameForm.name().value.set('Mont Terri');
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('mat-error')).toBeNull();
  });
});
