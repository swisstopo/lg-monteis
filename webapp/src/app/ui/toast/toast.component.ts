import {
  ChangeDetectionStrategy,
  Component,
  effect,
  inject,
  signal,
  TemplateRef,
  ViewChild,
  ViewEncapsulation,
} from '@angular/core';
import { MatMiniFabButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import {
  MatSnackBar,
  MatSnackBarAction,
  MatSnackBarActions,
  MatSnackBarHorizontalPosition,
  MatSnackBarLabel,
  MatSnackBarRef,
  MatSnackBarVerticalPosition,
} from '@angular/material/snack-bar';
import { ToastInfo, ToastService } from '../../shared/services/toast.service';

@Component({
  selector: 'app-toast',
  templateUrl: './toast.component.html',
  styleUrls: ['./toast.component.scss'],
  imports: [MatSnackBarLabel, MatSnackBarAction, MatSnackBarActions, MatIcon, MatMiniFabButton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None,
})
export class ToastComponent {
  private readonly _snackBar = inject(MatSnackBar);
  toastService = inject(ToastService);

  @ViewChild('toastTemplate') toastTemplate!: TemplateRef<unknown>;

  horizontalPosition = signal<MatSnackBarHorizontalPosition>('end');
  verticalPosition = signal<MatSnackBarVerticalPosition>('top');

  private previousToasts: readonly ToastInfo[] = [];
  private currentSnackBarRef?: MatSnackBarRef<unknown>;

  constructor() {
    effect(() => {
      const current = this.toastService.toasts();
      const newToasts = current.filter((toast) => !this.previousToasts.includes(toast));
      this.previousToasts = current;
      newToasts.forEach((toast) => this.openSnackBar(toast));
    });
  }

  openSnackBar(toast: ToastInfo) {
    this.currentSnackBarRef = this._snackBar.openFromTemplate(this.toastTemplate, {
      horizontalPosition: this.horizontalPosition(),
      verticalPosition: this.verticalPosition(),
      panelClass: toast.classname,
      data: toast,
      duration: 5000,
    });
  }

  dismiss() {
    this.currentSnackBarRef?.dismiss();
  }
}
