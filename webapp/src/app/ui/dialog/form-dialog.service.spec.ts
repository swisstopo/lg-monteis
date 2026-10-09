import { Component, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { FormDialogService } from './form-dialog.service';

@Component({ selector: 'app-greeting-dialog', template: '{{ name() }} {{ shouted() }}' })
class GreetingDialog {
  readonly name = input.required<string>();
  readonly shouted = input(false);
}

describe('FormDialogService', () => {
  let dialog: { open: ReturnType<typeof vi.fn> };
  let service: FormDialogService;

  beforeEach(() => {
    dialog = { open: vi.fn() };
    TestBed.configureTestingModule({ providers: [{ provide: MatDialog, useValue: dialog }] });
    service = TestBed.inject(FormDialogService);
  });

  it('opens every form dialog with the same size and focus', () => {
    service.open(GreetingDialog);

    expect(dialog.open).toHaveBeenCalledWith(
      GreetingDialog,
      expect.objectContaining({ width: '60vw', maxWidth: '1200px', autoFocus: true, bindings: [] }),
    );
  });

  it('binds every given input', () => {
    service.open(GreetingDialog, { name: 'alice', shouted: true });

    expect(dialog.open.mock.calls[0][1].bindings).toHaveLength(2);
  });

  it('checks input names and value types at compile time', () => {
    // @ts-expect-error no input of that name
    service.open(GreetingDialog, { nmae: 'alice' });
    // @ts-expect-error name takes a string
    service.open(GreetingDialog, { name: 42 });

    expect(dialog.open).toHaveBeenCalledTimes(2);
  });
});
