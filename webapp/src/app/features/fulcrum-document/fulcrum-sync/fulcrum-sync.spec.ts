import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { PermissionsService } from '@core/auth/permissions.service';
import { ErrorDto } from '@core/generated';
import { ToastService } from '@core/notifications/toast.service';
import { FulcrumDocumentService } from '@features/fulcrum-document/services/fulcrum-document.service';
import { syncStatus } from '@features/fulcrum-document/testing/fulcrum-document-fixtures';
import { provideTranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { FulcrumSync, SYNC_POLL_INTERVAL_MS } from './fulcrum-sync';

const RUNNING = syncStatus({ running: true });
const IDLE = syncStatus({ running: false });

describe('FulcrumSync', () => {
  let documentService: {
    getSyncStatus: ReturnType<typeof vi.fn>;
    startSync: ReturnType<typeof vi.fn>;
  };
  let toast: { error: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    documentService = {
      getSyncStatus: vi.fn().mockResolvedValue(IDLE),
      startSync: vi.fn().mockResolvedValue(RUNNING),
    };
    toast = { error: vi.fn() };
  });

  async function render(canAccessDocuments = true) {
    TestBed.configureTestingModule({
      imports: [FulcrumSync],
      providers: [
        provideTranslateService(),
        { provide: SYNC_POLL_INTERVAL_MS, useValue: 1 },
        { provide: FulcrumDocumentService, useValue: documentService },
        { provide: ToastService, useValue: toast },
        {
          provide: PermissionsService,
          useValue: { canAccessDocuments: signal(canAccessDocuments) },
        },
      ],
    });
    const fixture = TestBed.createComponent(FulcrumSync);
    const finished = vi.fn();
    fixture.componentInstance.finished.subscribe(finished);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    return {
      fixture,
      finished,
      refresh: () => element.querySelector('button'),
      status: () => element.querySelector('[data-testid="sync-status"]')?.textContent?.trim(),
      settle: () => fixture.whenStable(),
    };
  }

  it('shows when the last sync finished', async () => {
    const view = await render();

    expect(view.status()).toBe('fulcrumSync.lastSynced');
    expect(view.refresh()?.disabled).toBe(false);
  });

  it('says so when no sync ever ran', async () => {
    documentService.getSyncStatus.mockResolvedValue(syncStatus({ lastSyncedAt: undefined }));

    const view = await render();

    expect(view.status()).toBe('fulcrumSync.never');
  });

  it('shows nothing when the status cannot be loaded', async () => {
    documentService.getSyncStatus.mockRejectedValue(new HttpErrorResponse({ status: 403 }));

    const view = await render();

    expect(view.status()).toBe('');
    expect(toast.error).not.toHaveBeenCalled();
  });

  it('offers no refresh without document access', async () => {
    const view = await render(false);

    expect(view.refresh()).toBeNull();
    expect(view.status()).toBe('fulcrumSync.lastSynced');
  });

  it('runs a sync until it is no longer running, then reports it finished', async () => {
    documentService.getSyncStatus
      .mockResolvedValueOnce(IDLE)
      .mockResolvedValueOnce(RUNNING)
      .mockResolvedValueOnce(IDLE);
    const view = await render();

    view.refresh()!.click();
    await view.settle();

    expect(documentService.startSync).toHaveBeenCalledOnce();
    await vi.waitFor(() => expect(view.finished).toHaveBeenCalledOnce());
    await view.settle();
    expect(documentService.getSyncStatus).toHaveBeenCalledTimes(3);
    expect(view.refresh()?.disabled).toBe(false);
    expect(view.status()).toBe('fulcrumSync.lastSynced');
  });

  it('shows a running sync as running and disables the refresh', async () => {
    documentService.getSyncStatus.mockResolvedValueOnce(IDLE);
    documentService.getSyncStatus.mockReturnValue(new Promise(() => undefined));
    const view = await render();

    view.refresh()!.click();
    await vi.waitFor(() => expect(documentService.getSyncStatus).toHaveBeenCalledTimes(2));
    await view.settle();

    expect(view.refresh()?.disabled).toBe(true);
    expect(view.status()).toBe('fulcrumSync.running');
  });

  it('picks up a sync that is already running when the page opens', async () => {
    documentService.getSyncStatus.mockResolvedValueOnce(RUNNING).mockResolvedValueOnce(IDLE);

    const view = await render();

    await vi.waitFor(() => expect(view.finished).toHaveBeenCalledOnce());
    expect(documentService.startSync).not.toHaveBeenCalled();
  });

  it('treats a 409 already running as running and polls, without a toast', async () => {
    documentService.getSyncStatus
      .mockResolvedValueOnce(IDLE)
      .mockResolvedValueOnce(RUNNING)
      .mockResolvedValueOnce(IDLE);
    documentService.startSync.mockRejectedValue(
      new HttpErrorResponse({
        status: 409,
        error: { target: ErrorDto.TargetEnum.Global, messageKey: 'fulcrum.sync.alreadyRunning' },
      }),
    );
    const view = await render();

    view.refresh()!.click();

    await vi.waitFor(() => expect(view.finished).toHaveBeenCalledOnce());
    expect(documentService.getSyncStatus).toHaveBeenCalledTimes(3);
    expect(toast.error).not.toHaveBeenCalled();
  });

  it('toasts a sync that could not be started', async () => {
    documentService.startSync.mockRejectedValue(new HttpErrorResponse({ status: 500 }));
    const view = await render();

    view.refresh()!.click();

    await vi.waitFor(() => expect(toast.error).toHaveBeenCalledWith('fulcrumSync.error.start'));
    await view.settle();
    expect(view.refresh()?.disabled).toBe(false);
    expect(view.finished).not.toHaveBeenCalled();
  });

  it('stops polling and says so when the status cannot be loaded', async () => {
    documentService.getSyncStatus
      .mockResolvedValueOnce(IDLE)
      .mockRejectedValueOnce(new HttpErrorResponse({ status: 500 }));
    const view = await render();

    view.refresh()!.click();

    await vi.waitFor(() => expect(documentService.getSyncStatus).toHaveBeenCalledTimes(2));
    await view.settle();
    expect(view.status()).toBe('fulcrumSync.statusUnknown');
    expect(view.refresh()?.disabled).toBe(false);
  });
});
