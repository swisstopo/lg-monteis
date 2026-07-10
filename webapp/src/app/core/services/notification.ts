import { inject, Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateService } from '@ngx-translate/core';

@Injectable({
  providedIn: 'root',
})
export class Notification {
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);

  success(params?: object) {
    const key: string = 'sensor.create.success';
    this.open(key, params, ['toast-success']);
  }

  error(key: string, params?: object) {
    this.open(key, params, ['toast-error']);
  }

  info(key: string, params?: object) {
    this.open(key, params);
  }

  private open(key: string, params?: object, panelClass: string[] = []) {
    this.snackBar.open(
      this.translate.instant(key, params),
      this.translate.instant('common.close'),
      {
        duration: 5000,
        horizontalPosition: 'right',
        verticalPosition: 'top',
        panelClass,
      },
    );
  }
}
