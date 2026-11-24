import { Component, signal, Type } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap, Params } from '@angular/router';
import { WorkbenchRouter, WorkbenchView } from '@scion/workbench';
import Table from '@ui/table/table';
import { UrlFilter } from '@ui/table/url-filter';
import { AgGridAngular } from 'ag-grid-angular';
import {
  AllCommunityModule,
  FilterModel,
  IDatasource,
  IGetRowsParams,
  ModuleRegistry,
  TextFilterModel,
} from 'ag-grid-community';
import { BehaviorSubject, map } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

// registered in main.ts, test bed never runs it
ModuleRegistry.registerModules([AllCommunityModule]);

interface Row {
  key: string;
}

const ROWS: Row[] = [{ key: 'DAS-1' }, { key: 'DAS-2' }, { key: 'DAS-12' }];
const COLUMNS = [{ field: 'key' as const }];

function equals(filter: string): TextFilterModel {
  return { filterType: 'text', type: 'equals', filter };
}

function contains(filter: string): TextFilterModel {
  return { filterType: 'text', type: 'contains', filter };
}

// the paged backend's filtering, reduced to the two text filter types the tests use
function filterRows(filterModel: FilterModel | undefined): Row[] {
  const model = filterModel?.['key'] as TextFilterModel | undefined;
  if (!model?.filter) return ROWS;
  return ROWS.filter((row) =>
    model.type === 'equals' ? row.key === model.filter : row.key.includes(model.filter!),
  );
}

@Component({
  imports: [Table, UrlFilter],
  template: `
    <app-table
      appUrlFilter
      [columns]="columns"
      [rowModelType]="'infinite'"
      [datasource]="datasource"
      [getRowId]="getRowId"
      (gridReady)="ready.set(true)"
    />
  `,
})
class PagedTableHost {
  readonly requests: (FilterModel | undefined)[] = [];
  readonly ready = signal(false);
  protected readonly columns = COLUMNS;
  protected readonly getRowId = (row: Row): string => row.key;
  protected readonly datasource: IDatasource = {
    getRows: (params: IGetRowsParams) => {
      this.requests.push(params.filterModel as FilterModel);
      const rows = filterRows(params.filterModel as FilterModel);
      params.successCallback(rows, rows.length);
    },
  };
}

@Component({
  imports: [Table, UrlFilter],
  template: `
    <app-table
      appUrlFilter
      [columns]="columns"
      [rows]="rows"
      [getRowId]="getRowId"
      (gridReady)="ready.set(true)"
    />
  `,
})
class LoadedTableHost {
  readonly ready = signal(false);
  protected readonly columns = COLUMNS;
  protected readonly rows = ROWS;
  protected readonly getRowId = (row: Row): string => row.key;
}

interface Host {
  ready: () => boolean;
}

let params: BehaviorSubject<Params>;
let route: Partial<ActivatedRoute>;
let workbenchRouter: { navigate: ReturnType<typeof vi.fn> };

beforeEach(() => {
  params = new BehaviorSubject<Params>({});
  route = { paramMap: params.pipe(map(convertToParamMap)) };
  workbenchRouter = { navigate: vi.fn(() => Promise.resolve(true)) };
});

async function render<T extends Host>(
  host: Type<T>,
  filterModel?: FilterModel,
): Promise<TableView<T>> {
  navigateTo(filterModel);
  return renderWithCurrentParams(host);
}

async function renderWithCurrentParams<T extends Host>(host: Type<T>): Promise<TableView<T>> {
  TestBed.configureTestingModule({
    imports: [host],
    providers: [
      { provide: ActivatedRoute, useValue: route },
      { provide: WorkbenchView, useValue: { id: 'view.3' } },
      { provide: WorkbenchRouter, useValue: workbenchRouter },
    ],
  });
  const fixture = TestBed.createComponent(host);
  await fixture.whenStable();
  return new TableView(fixture);
}

function navigateTo(filterModel: FilterModel | undefined): void {
  params.next(filterModel ? { filter: JSON.stringify(filterModel) } : {});
}

function expectUrlFilter(filterModel: FilterModel | undefined): Promise<void> {
  const matrixParams = filterModel ? { filter: JSON.stringify(filterModel) } : {};
  return vi.waitFor(() =>
    expect(workbenchRouter.navigate).toHaveBeenLastCalledWith([matrixParams], {
      relativeTo: route,
      target: 'view.3',
      replaceUrl: true,
    }),
  );
}

describe('UrlFilter on a paged table', () => {
  it('requests the first page once and filtered to the filter param', async () => {
    const table = await render(PagedTableHost, { key: equals('DAS-1') });
    await table.shows(['DAS-1']);

    expect(table.host().requests).toEqual([{ key: equals('DAS-1') }]);
  });
});

describe.each<{ name: string; host: Type<Host> }>([
  { name: 'paged table', host: PagedTableHost },
  { name: 'fully loaded table', host: LoadedTableHost },
])('UrlFilter on a $name', ({ host }) => {
  it('shows all rows without a filter param', async () => {
    const table = await render(host);

    await vi.waitFor(() => expect(table.shownKeys()).toEqual(['DAS-1', 'DAS-2', 'DAS-12']));
  });

  it('shows only the rows of the filter param from the first load on', async () => {
    const table = await render(host, { key: equals('DAS-1') });

    await vi.waitFor(() => expect(table.shownKeys()).toEqual(['DAS-1']));
  });

  it('shows the filter param in the column filter', async () => {
    const table = await render(host, { key: equals('DAS-1') });

    await vi.waitFor(() => expect(table.floatingFilter('key')).toBe('DAS-1'));
  });

  it('shows all rows when the filter param is no filter model', async () => {
    params.next({ filter: '{not json' });
    const table = await renderWithCurrentParams(host);

    await vi.waitFor(() => expect(table.shownKeys()).toEqual(['DAS-1', 'DAS-2', 'DAS-12']));
  });

  it('drops the filter param when the filter is cleared in the grid', async () => {
    const table = await render(host, { key: equals('DAS-1') });
    await table.shows(['DAS-1']);

    table.typeInFloatingFilter('key', '');

    await expectUrlFilter(undefined);
  });

  it('writes a changed filter value to the filter param', async () => {
    const table = await render(host, { key: equals('DAS-1') });
    await table.shows(['DAS-1']);

    table.typeInFloatingFilter('key', 'DAS-2');

    await expectUrlFilter({ key: equals('DAS-2') });
  });

  it('writes a changed filter type to the filter param', async () => {
    const table = await render(host, { key: equals('DAS-1') });
    await table.shows(['DAS-1']);

    table.changeFilter('key', contains('DAS-1'));

    await expectUrlFilter({ key: contains('DAS-1') });
  });

  it('writes a filter set without a filter param to the filter param', async () => {
    const table = await render(host);
    await table.shows(['DAS-1', 'DAS-2', 'DAS-12']);

    table.typeInFloatingFilter('key', 'DAS-1');

    await expectUrlFilter({ key: contains('DAS-1') });
  });

  it('shows the rows of another filter param when the view is navigated to it', async () => {
    const table = await render(host, { key: equals('DAS-1') });
    await table.shows(['DAS-1']);

    navigateTo({ key: equals('DAS-2') });

    await vi.waitFor(() => expect(table.shownKeys()).toEqual(['DAS-2']));
    expect(workbenchRouter.navigate).not.toHaveBeenCalled();
  });

  it('shows all rows when the view is navigated without a filter param', async () => {
    const table = await render(host, { key: equals('DAS-1') });
    await table.shows(['DAS-1']);

    navigateTo(undefined);

    await vi.waitFor(() => expect(table.shownKeys()).toEqual(['DAS-1', 'DAS-2', 'DAS-12']));
    expect(workbenchRouter.navigate).not.toHaveBeenCalled();
  });
});

class TableView<T extends Host> {
  constructor(private readonly fixture: ComponentFixture<T>) {}

  host(): T {
    return this.fixture.componentInstance;
  }

  async shows(keys: string[]): Promise<void> {
    await vi.waitFor(() => expect(this.shownKeys()).toEqual(keys));
    // client-side grid renders rows before gridReady, edits before it never reach the directive
    await vi.waitFor(() => expect(this.fixture.componentInstance.ready()).toBe(true));
  }

  shownKeys(): string[] {
    const element = this.fixture.nativeElement as HTMLElement;
    return [...element.querySelectorAll<HTMLElement>('.ag-row[row-index]')]
      .sort((a, b) => Number(a.getAttribute('row-index')) - Number(b.getAttribute('row-index')))
      .map((row) => row.querySelector('[col-id="key"]')?.textContent?.trim() ?? '');
  }

  floatingFilter(colId: string): string | undefined {
    return this.floatingFilterInput(colId)?.value;
  }

  typeInFloatingFilter(colId: string, value: string): void {
    const input = this.floatingFilterInput(colId)!;
    input.value = value;
    input.dispatchEvent(new Event('input', { bubbles: true }));
  }

  // operator dropdown lives in a popup, set the model through the grid like the popup does
  changeFilter(colId: string, model: TextFilterModel): void {
    const { api } = this.fixture.debugElement.query(By.directive(AgGridAngular))
      .componentInstance as AgGridAngular;
    api.setFilterModel({ [colId]: model });
  }

  private floatingFilterInput(colId: string): HTMLInputElement | null {
    const element = this.fixture.nativeElement as HTMLElement;
    return element.querySelector<HTMLInputElement>(
      `.ag-header-cell.ag-floating-filter[col-id="${colId}"] input`,
    );
  }
}
