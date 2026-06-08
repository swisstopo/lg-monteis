import { Component, computed, input, output } from '@angular/core';
import { AgGridAngular } from 'ag-grid-angular';
import { ColDef, GridOptions, themeBalham } from 'ag-grid-community';
import { TableColumn } from './table.types';

@Component({
  selector: 'app-table',
  imports: [AgGridAngular],
  templateUrl: './table.html',
  styleUrl: './table.scss',
})
export default class Table<T = any> {
  rows = input<T[]>([]);
  columns = input<TableColumn<T>[]>([]);

  rowClicked = output<any>();

  columnDefs = computed<ColDef[]>(() => this.mapColumns(this.columns()));
  protected theme = themeBalham;

  defaultColDef: ColDef = {
    sortable: false,
    filter: true,
    resizable: false,
    flex: 1,
    minWidth: 120,
  };

  private mapColumns(cols: TableColumn<T>[]): ColDef[] {
    return cols.map((col) => ({
      field: col.field as string,
      type: col.type,
      headerName: col.header ?? this.toHeader(col.field as string),
      sortable: col.sortable,
      filter: col.filter,
      resizable: col.resizable,

      width: col.width,
      flex: col.flex,
      valueFormatter: col.valueFormatter,
    }));
  }

  protected gridOptions: GridOptions = {
    suppressCellFocus: true,
    domLayout: 'autoHeight',
  };

  private toHeader(field: string): string {
    return field.replace(/([A-Z])/g, '$1').replace(/^./, (s) => s.toUpperCase());
  }

  onRowClicked(event: any): void {
    this.rowClicked.emit(event.data);
  }
}
