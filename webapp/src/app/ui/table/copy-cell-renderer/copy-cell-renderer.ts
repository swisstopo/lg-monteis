import { Clipboard } from '@angular/cdk/clipboard';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { ToastService } from '@core/notifications/toast.service';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ICellRendererAngularComp } from 'ag-grid-angular';
import { ICellRendererParams } from 'ag-grid-community';

/**
 * Ag-grid cellRenderer showing the formatted cell value alongside a button that copies the raw
 * value to the clipboard, e.g. for the experiment id needed to configure Keycloak RLS.
 */
@Component({
  selector: 'app-copy-cell-renderer',
  templateUrl: './copy-cell-renderer.html',
  styleUrl: './copy-cell-renderer.scss',
  imports: [MatIconButton, MatIcon, TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CopyCellRenderer implements ICellRendererAngularComp {
  private readonly clipboard = inject(Clipboard);
  private readonly toastService = inject(ToastService);
  private readonly translateService = inject(TranslateService);

  protected displayValue = signal('');
  private rawValue = '';

  agInit(params: ICellRendererParams): void {
    this.setValue(params);
  }

  refresh(params: ICellRendererParams): boolean {
    this.setValue(params);
    return true;
  }

  protected copy(): void {
    if (!this.rawValue) {
      return;
    }
    this.clipboard.copy(this.rawValue);
    this.toastService.success(this.translateService.translate('common.copy.success')());
  }

  private setValue(params: ICellRendererParams): void {
    this.rawValue = params.value ?? '';
    this.displayValue.set(params.valueFormatted ?? this.rawValue);
  }
}
