import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar, MatSnackBarRef } from '@angular/material/snack-bar';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../shared/services/toast.service';
import { ToastComponent } from './toast.component';

describe('ToastComponent', () => {
  let fixture: ComponentFixture<ToastComponent>;
  let component: ToastComponent;
  let toastService: ToastService;
  let snackBarRef: MatSnackBarRef<unknown>;
  let openFromTemplateSpy: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    snackBarRef = { dismiss: vi.fn() } as unknown as MatSnackBarRef<unknown>;
    openFromTemplateSpy = vi.fn().mockReturnValue(snackBarRef);

    await TestBed.configureTestingModule({
      imports: [ToastComponent],
      providers: [
        ToastService,
        { provide: MatSnackBar, useValue: { openFromTemplate: openFromTemplateSpy } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ToastComponent);
    component = fixture.componentInstance;
    toastService = TestBed.inject(ToastService);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('opens a snack bar when a toast is added', () => {
    toastService.success('Operation completed', 'Success');
    fixture.detectChanges();

    expect(openFromTemplateSpy).toHaveBeenCalledTimes(1);
    expect(openFromTemplateSpy).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({
        horizontalPosition: 'end',
        verticalPosition: 'top',
        panelClass: 'snackbar-success',
        duration: 5000,
        data: expect.objectContaining({
          message: 'Operation completed',
          header: 'Success',
          classname: 'snackbar-success',
          icon: 'check_circle',
        }),
      }),
    );
  });

  it('opens multiple snack bars for consecutive toasts', () => {
    toastService.success('First');
    toastService.warning('Second');
    toastService.error('Third');
    fixture.detectChanges();

    expect(openFromTemplateSpy).toHaveBeenCalledTimes(3);
    expect(openFromTemplateSpy.mock.calls[0][1].panelClass).toBe('snackbar-success');
    expect(openFromTemplateSpy.mock.calls[1][1].panelClass).toBe('snackbar-warning');
    expect(openFromTemplateSpy.mock.calls[2][1].panelClass).toBe('snackbar-error');
  });

  it('does not reopen existing toasts on redundant signal notifications', () => {
    toastService.success('Only once');
    fixture.detectChanges();

    // Re-emitting the same array should not trigger new snack bars.
    fixture.detectChanges();
    expect(openFromTemplateSpy).toHaveBeenCalledTimes(1);
  });

  it('dismisses the current snack bar when dismiss is called', () => {
    toastService.success('Dismiss me');
    fixture.detectChanges();

    component.dismiss();

    expect(snackBarRef.dismiss).toHaveBeenCalledTimes(1);
  });
});
