import { Component, computed, input, output } from '@angular/core';
import { AgGridAngular } from 'ag-grid-angular';
import {
  ColDef,
  GridOptions,
  RowClickedEvent,
  RowSelectionOptions,
  SelectionChangedEvent,
  themeBalham,
} from 'ag-grid-community';
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
  // Whether to show row-selection checkboxes; independent per table instance.
  checkboxes = input<boolean>(false);
  // Whether more than one row can be selected at once; independent per table instance.
  multiple = input<boolean>(false);
  // Passthrough for grid-level ag-grid settings; merged on top of this component's defaults so
  // any table can opt into/out of individual ag-grid options without changing this component.
  gridOptions = input<GridOptions<T>>({});

  rowClicked = output<T>();
  selectionChanged = output<T[]>();

  protected theme = themeBalham;

  defaultColDef: ColDef<T> = {
    sortable: false,
    filter: true,
    // Always show the filter input under the header instead of requiring a click to open it.
    floatingFilter: true,
    resizable: true,
    flex: 1,
    minWidth: 120,
  };

  protected mergedGridOptions = computed<GridOptions<T>>(() => {
    const rowSelection: RowSelectionOptions<T> = this.multiple()
      ? {
          mode: 'multiRow',
          checkboxes: this.checkboxes(),
          headerCheckbox: this.checkboxes(),
          enableClickSelection: true,
        }
      : {
          mode: 'singleRow',
          checkboxes: this.checkboxes(),
          enableClickSelection: true,
        };

    return {
      suppressCellFocus: true,
      domLayout: 'autoHeight',
      rowSelection,
      ...this.gridOptions(),
    };
  });

  onRowClicked(event: RowClickedEvent<T>): void {
    if (event.data) this.rowClicked.emit(event.data);
  }

  onSelectionChanged(event: SelectionChangedEvent<T>): void {
    this.selectionChanged.emit(event.api.getSelectedRows());
  }
}
