import { Component, computed, input, output, signal } from '@angular/core';
import { AgGridAngular } from 'ag-grid-angular';
import {
  ColDef,
  GetRowIdParams,
  GridApi,
  GridOptions,
  GridReadyEvent,
  IDatasource,
  RowClickedEvent,
  RowModelType,
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
  // 'infinite' opts a table into ag-grid's Infinite Row Model, fetching rows in blocks from
  // `datasource` instead of rendering the full `rows` array client-side.
  rowModelType = input<RowModelType>('clientSide');
  datasource = input<IDatasource | undefined>(undefined);
  // Number of rows ag-grid requests per `datasource.getRows` call, in 'infinite' mode.
  cacheBlockSize = input<number>(100);
  // Passthrough for grid-level ag-grid settings; merged on top of this component's defaults so
  // any table can opt into/out of individual ag-grid options without changing this component.
  gridOptions = input<GridOptions<T>>({});
  // Stable-id extractor for a row, e.g. `(row) => String(row.id)`. Required so selection can
  // survive ag-grid's infinite row model evicting/re-fetching cache blocks.
  getRowId = input.required<(data: T) => string>();

  rowClicked = output<T>();
  selectionChanged = output<T[]>();
  // Exposes the underlying ag-grid API so callers can drive it directly, e.g. to refresh an
  // 'infinite' row model's cache after data changes elsewhere (ag-grid has no way to detect that).
  gridReady = output<GridApi<T>>();

  protected theme = themeBalham;
  // Ids (via getRowId) of the currently selected rows, tracked so a later 'infinite' row model
  // cache refresh can tell whether a selected row was evicted and needs deselecting.
  private readonly selectedIds = signal<Set<string>>(new Set());

  defaultColDef: ColDef<T> = {
    sortable: false,
    filter: true,
    // Always show the filter input under the header instead of requiring a click to open it.
    floatingFilter: true,
    resizable: true,
    flex: 1,
    minWidth: 120,
    // Only a single filter condition per column is supported by the paged backend endpoint, so
    // combined ("AND"/"OR") filters are disabled here to keep the filter model ag-grid sends in sync
    // with what the backend can translate.
    filterParams: { maxNumConditions: 1 },
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
      // Lets colDef.tooltipField/tooltipValueGetter show the native browser tooltip, e.g. to
      // reveal a truncated cell's full text on hover.
      enableBrowserTooltips: true,
      rowSelection,
      getRowId: (params: GetRowIdParams<T>) => this.getRowId()(params.data),
      ...this.gridOptions(),
    };
  });

  onRowClicked(event: RowClickedEvent<T>): void {
    if (event.data) this.rowClicked.emit(event.data);
  }

  onSelectionChanged(event: SelectionChangedEvent<T>): void {
    const rows = event.api.getSelectedRows();
    this.selectedIds.set(new Set(rows.map(this.getRowId())));
    this.selectionChanged.emit(rows);
  }

  onGridReady(event: GridReadyEvent<T>): void {
    this.gridReady.emit(event.api);

    if (this.rowModelType() === 'infinite') {
      event.api.addEventListener('modelUpdated', () => this.deselectEvictedRows(event.api));
    }
  }

  // Ag-grid's infinite row model can evict a selected row's block from its cache on refresh
  // without ag-grid itself noticing the selection is now stale - detect and clear it here.
  private deselectEvictedRows(api: GridApi<T>): void {
    const selectedIds = this.selectedIds();
    if (selectedIds.size === 0) return;

    const presentIds = new Set<string>();
    api.forEachNode((node) => {
      if (node.data) presentIds.add(this.getRowId()(node.data));
    });

    if ([...selectedIds].some((id) => !presentIds.has(id))) {
      api.deselectAll();
    }
  }
}
