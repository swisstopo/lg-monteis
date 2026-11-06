import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideTranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ConfirmDeletionService } from './confirm-deletion-dialog';

const INPUTS = {
  title: 'Delete document',
  message: '“report.pdf” will be permanently deleted.',
  confirmLabel: 'Delete',
};

describe('ConfirmDeletionService', () => {
  let service: ConfirmDeletionService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideTranslateService()] });
    service = TestBed.inject(ConfirmDeletionService);
  });

  afterEach(() => {
    TestBed.inject(MatDialog).closeAll();
  });

  async function openDialog() {
    const confirmed = service.confirmDeletion(INPUTS);
    const dialog = await vi.waitFor(() => {
      const element = document.querySelector<HTMLElement>('app-confirm-deletion-dialog');
      expect(element?.textContent).toContain(INPUTS.message);
      return element!;
    });
    return { confirmed, dialog };
  }

  it('shows the title, the message and the confirm label', async () => {
    const { dialog } = await openDialog();

    expect(dialog.querySelector('h2')?.textContent).toContain('Delete document');
    expect(dialog.querySelector('.confirm')?.textContent).toContain('Delete');
  });

  it('resolves true when the deletion is confirmed', async () => {
    const { confirmed, dialog } = await openDialog();

    dialog.querySelector<HTMLButtonElement>('.confirm')!.click();

    await expect(confirmed).resolves.toBe(true);
  });

  it('resolves false when the dialog is cancelled', async () => {
    const { confirmed, dialog } = await openDialog();

    dialog.querySelector<HTMLButtonElement>('app-dismiss-button button')!.click();

    await expect(confirmed).resolves.toBe(false);
  });
});
