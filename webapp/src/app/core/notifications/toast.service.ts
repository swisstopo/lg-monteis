import { Injectable, signal } from '@angular/core';

export interface ToastAction {
  label: string;
  onClick: () => void;
}

export interface ToastInfo {
  message: string;
  header?: string;
  classname?: string;
  icon?: 'check_circle' | 'error' | 'warning';
  action?: ToastAction;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly toastsSignal = signal<readonly ToastInfo[]>([]);
  toasts = this.toastsSignal.asReadonly();

  constructor() {}

  success(message: string, header?: string, action?: ToastAction) {
    this.add({
      message,
      header,
      classname: 'snackbar-success',
      icon: 'check_circle',
      action,
    });
  }

  warning(message: string, header?: string, action?: ToastAction) {
    this.add({
      message,
      header,
      classname: 'snackbar-warning',
      icon: 'warning',
      action,
    });
  }

  error(message: string, header?: string, action?: ToastAction) {
    this.add({
      message,
      header,
      classname: 'snackbar-error',
      icon: 'error',
      action,
    });
  }

  private add(toast: ToastInfo): void {
    this.toastsSignal.update((toasts) => [...toasts, toast]);
  }
}
