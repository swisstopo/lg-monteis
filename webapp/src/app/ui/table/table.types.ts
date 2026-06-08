import { ValueFormatterFunc } from 'ag-grid-community';

export interface TableColumn<T = any> {
  field: keyof T | string;
  type?: string | string[];
  header?: string;

  sortable?: boolean;
  filter?: boolean;
  resizable?: boolean;

  width?: number;
  flex?: number;
  valueFormatter?: ValueFormatterFunc<T>;
}
