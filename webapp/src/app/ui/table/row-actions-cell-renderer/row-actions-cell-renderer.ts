import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { TranslatePipe } from '@ngx-translate/core';
import { TableColumn } from '@ui/table/table.types';
import { ICellRendererAngularComp } from 'ag-grid-angular';
import { ICellRendererParams } from 'ag-grid-community';

export interface RowAction<TData> {
  labelKey: string;
  icon?: string;
  disabled?: (row: TData) => boolean;
  run: (row: TData) => void;
}

interface RowActionsParams<TData> {
  actions: RowAction<TData>[];
}

export function rowActionsColumn<TData>(actions: RowAction<TData>[]): TableColumn<TData> {
  const params: RowActionsParams<TData> = { actions };
  return {
    colId: 'actions',
    headerName: '',
    cellRenderer: RowActionsCellRenderer,
    cellRendererParams: params,
    sortable: false,
    filter: false,
    floatingFilter: false,
    resizable: false,
    width: 64,
  };
}

@Component({
  selector: 'app-row-actions-cell-renderer',
  imports: [MatIconButton, MatIcon, MatMenu, MatMenuItem, MatMenuTrigger, TranslatePipe],
  template: `
    <button
      mat-icon-button
      type="button"
      [matMenuTriggerFor]="menu"
      [attr.aria-label]="'common.rowActions' | translate"
    >
      <mat-icon fontIcon="more_vert"></mat-icon>
    </button>
    <mat-menu #menu="matMenu">
      @for (action of actions(); track action.labelKey) {
        <button mat-menu-item type="button" [disabled]="isDisabled(action)" (click)="run(action)">
          @if (action.icon) {
            <mat-icon [fontIcon]="action.icon"></mat-icon>
          }
          <span>{{ action.labelKey | translate }}</span>
        </button>
      }
    </mat-menu>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RowActionsCellRenderer<TData> implements ICellRendererAngularComp {
  protected readonly actions = signal<RowAction<TData>[]>([]);
  private readonly row = signal<TData | undefined>(undefined);

  agInit(params: ICellRendererParams<TData> & RowActionsParams<TData>): void {
    this.refresh(params);
  }

  refresh(params: ICellRendererParams<TData> & RowActionsParams<TData>): boolean {
    this.actions.set(params.actions);
    this.row.set(params.data);
    return true;
  }

  protected isDisabled(action: RowAction<TData>): boolean {
    const row = this.row();
    return row === undefined || (action.disabled?.(row) ?? false);
  }

  protected run(action: RowAction<TData>): void {
    const row = this.row();
    if (row !== undefined) action.run(row);
  }
}
