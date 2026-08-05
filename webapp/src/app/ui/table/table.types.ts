import { ColDef } from 'ag-grid-community';

/**
 * Alias for ag-grid's own ColDef so every table's columns.ts gets full access to ag-grid's
 * column API (wrapText, autoHeight, cellRenderer, pinned, comparator, ...) without this project
 * re-declaring a subset of it.
 */
export type TableColumn<TData = any, TValue = any> = ColDef<TData, TValue>;
