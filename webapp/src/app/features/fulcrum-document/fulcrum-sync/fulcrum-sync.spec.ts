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
import { FulcrumSync } from './fulcrum-sync';

const RUNNING = syncStatus({ running: true });
const IDLE = syncStatus({ running: false });

describe('FulcrumSync', () => {
  let documentService: {
    getSyncStatus: ReturnType<typeof vi.fn>;
    startSync: ReturnType<typeof vi.fn>;
  };
  let toast: {
    success: ReturnType<typeof vi.fn>;
    warning: ReturnType<typeof vi.fn>;
    error: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    documentService = {
      getSyncStatus: vi.fn().mockResolvedValue(IDLE),
      startSync: vi.fn().mockResolvedValue(RUNNING),
    };
    toast = { success: vi.fn(), warning: vi.fn(), error: vi.fn() };
  });

  async function render(canAccessDocuments = true) {
    TestBed.configureTestingModule({
      imports: [FulcrumSync],
      providers: [
        provideTranslateService(),
        { provide: FulcrumDocumentService, useValue: documentService },
        { provide: ToastService, useValue: toast },
        {
          provide: PermissionsService,
          useValue: { canAccessDocuments: signal(canAccessDocuments) },
        },
      ],
    });
    const fixture = TestBed.createComponent(FulcrumSync);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    return {
      fixture,
      refresh: () => element.querySelector('button'),
      status: () => element.querySelector('[data-testid="sync-status"]')?.textContent?.trim(),
      settle: () => fixture.whenStable(),
    };
  }

  it('shows when the last sync finished', async () => {
    const view = await render();

    expect(view.status()).toBe('fulcrumSync.lastSuccessfulSync');
    expect(view.refresh()?.disabled).toBe(false);
  });

  it('says so when no sync ever ran', async () => {
    documentService.getSyncStatus.mockResolvedValue(
      syncStatus({ lastSuccessfulSyncAt: undefined }),
    );

    const view = await render();

    expect(view.status()).toBe('fulcrumSync.never');
  });

  it('says so when the status cannot be loaded', async () => {
    documentService.getSyncStatus.mockRejectedValue(new HttpErrorResponse({ status: 500 }));

    const view = await render();

    expect(view.status()).toBe('fulcrumSync.statusUnknown');
    expect(toast.error).not.toHaveBeenCalled();
  });

  it('shows a sync that runs when the page opens as running', async () => {
    documentService.getSyncStatus.mockResolvedValue(RUNNING);

    const view = await render();

    expect(view.status()).toBe('fulcrumSync.running');
    expect(view.refresh()?.disabled).toBe(false);
  });

  it('offers no refresh without document access', async () => {
    const view = await render(false);

    expect(view.refresh()).toBeNull();
    expect(view.status()).toBe('fulcrumSync.lastSuccessfulSync');
  });

  it('starts a sync once and says it started, without asking for the status again', async () => {
    const view = await render();

    view.refresh()!.click();
    await vi.waitFor(() => expect(toast.success).toHaveBeenCalledWith('fulcrumSync.started'));
    await view.settle();

    expect(documentService.startSync).toHaveBeenCalledOnce();
    expect(documentService.getSyncStatus).toHaveBeenCalledOnce();
    expect(view.refresh()?.disabled).toBe(false);
    expect(view.status()).toBe('fulcrumSync.lastSuccessfulSync');
  });

  it('disables the refresh while the sync is being started', async () => {
    documentService.startSync.mockReturnValue(new Promise(() => undefined));
    const view = await render();

    view.refresh()!.click();
    await view.settle();

    expect(view.refresh()?.disabled).toBe(true);
  });

  it('warns when a sync is already running', async () => {
    documentService.startSync.mockRejectedValue(
      new HttpErrorResponse({
        status: 409,
        error: { target: ErrorDto.TargetEnum.Global, messageKey: 'fulcrum.sync.alreadyRunning' },
      }),
    );
    const view = await render();

    view.refresh()!.click();

    await vi.waitFor(() =>
      expect(toast.warning).toHaveBeenCalledWith('fulcrum.sync.alreadyRunning'),
    );
    await view.settle();
    expect(toast.error).not.toHaveBeenCalled();
    expect(view.refresh()?.disabled).toBe(false);
  });

  it('toasts a sync that could not be started', async () => {
    documentService.startSync.mockRejectedValue(new HttpErrorResponse({ status: 500 }));
    const view = await render();

    view.refresh()!.click();

    await vi.waitFor(() => expect(toast.error).toHaveBeenCalledWith('fulcrumSync.error.start'));
    await view.settle();
    expect(view.refresh()?.disabled).toBe(false);
  });
});
