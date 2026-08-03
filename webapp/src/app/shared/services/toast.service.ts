import { Injectable, signal } from '@angular/core';

export interface ToastInfo {
  message: string;
  header?: string;
  classname?: string;
  icon?: 'check_circle' | 'error' | 'warning';
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly toastsSignal = signal<readonly ToastInfo[]>([]);
  toasts = this.toastsSignal.asReadonly();

  constructor() {}

  success(message: string, header?: string) {
    this.add({
      message,
      header,
      classname: 'snackbar-success',
      icon: 'check_circle',
    });
  }

  warning(message: string, header?: string) {
    this.add({
      message,
      header,
      classname: 'snackbar-warning',
      icon: 'warning',
    });
  }

  error(message: string, header?: string) {
    this.add({
      message,
      header,
      classname: 'snackbar-error',
      icon: 'error',
    });
  }

  private add(toast: ToastInfo): void {
    this.toastsSignal.update((toasts) => [...toasts, toast]);
  }
}
