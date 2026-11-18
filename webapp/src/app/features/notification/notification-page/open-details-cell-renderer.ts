import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { NotifyingParameterResponseDto } from '@core/generated';
import { TranslatePipe } from '@ngx-translate/core';
import { WorkbenchRouter } from '@scion/workbench';
import { ICellRendererAngularComp } from 'ag-grid-angular';
import { ICellRendererParams } from 'ag-grid-community';

@Component({
  selector: 'app-open-details-cell-renderer',
  imports: [MatButton, TranslatePipe],
  template: `
    <button matButton="text" [disabled]="!dasKey()" (click)="openDetails()">
      {{ 'notification.parameters.openDetails' | translate }}
    </button>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OpenDetailsCellRenderer implements ICellRendererAngularComp {
  private readonly workbenchRouter = inject(WorkbenchRouter);
  // no DAS key = no reading yet, nothing to show
  protected readonly dasKey = signal<string | undefined>(undefined);

  agInit(params: ICellRendererParams<NotifyingParameterResponseDto>): void {
    this.refresh(params);
  }

  refresh(params: ICellRendererParams<NotifyingParameterResponseDto>): boolean {
    this.dasKey.set(params.data?.dasKey);
    return true;
  }

  // blank, not auto: auto would re-filter every open measurement table tab
  protected openDetails(): void {
    void this.workbenchRouter.navigate(['/measurements-table', { dasKey: this.dasKey() }], {
      target: 'blank',
    });
  }
}
