import { Directive, effect, inject, Signal, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { WorkbenchRouter, WorkbenchView } from '@scion/workbench';
import Table, { TABLE_INITIAL_STATE } from '@ui/table/table';
import { TABLE_FILTER_PARAM, tableFilterParams } from '@ui/table/table-link';
import {
  FilterModel,
  GridApi,
  GridState,
  GridStateKey,
  StateUpdatedEvent,
} from 'ag-grid-community';
import { map } from 'rxjs';

const SYNCED_STATE: GridStateKey[] = ['filter'];

function sameFilter(a: FilterModel | undefined, b: FilterModel): boolean {
  return JSON.stringify(a ?? {}) === JSON.stringify(b);
}

// hand-edited or outdated link => unfiltered table instead of a broken tab
function parseFilterModel(json: string | null): FilterModel {
  if (!json) return {};
  try {
    const parsed: unknown = JSON.parse(json);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? (parsed as FilterModel)
      : {};
  } catch {
    return {};
  }
}

function filterModelFromUrl(): Signal<FilterModel> {
  return toSignal(
    inject(ActivatedRoute).paramMap.pipe(
      map((params) => parseFilterModel(params.get(TABLE_FILTER_PARAM))),
    ),
    { initialValue: {} },
  );
}

// initialState, not setState on grid ready: paged grid requests its 1st page while created.
// own factory, not inject(UrlFilter): Table injects the token while UrlFilter injects Table
function initialStateFromUrl(): () => GridState {
  const filterModel = filterModelFromUrl();
  return () => ({ filter: { filterModel: filterModel() } });
}

@Directive({
  selector: 'app-table[appUrlFilter]',
  providers: [{ provide: TABLE_INITIAL_STATE, useFactory: initialStateFromUrl }],
})
export class UrlFilter {
  private readonly workbenchRouter = inject(WorkbenchRouter);
  private readonly view = inject(WorkbenchView);
  private readonly route = inject(ActivatedRoute);

  private readonly urlFilterModel = filterModelFromUrl();
  private readonly gridApi = signal<GridApi | undefined>(undefined);

  constructor() {
    inject(Table).gridReady.subscribe((api) => this.onGridReady(api));

    // view reused on navigation (menu, back), only the matrix param changes
    effect(() => {
      const api = this.gridApi();
      const filterModel = this.urlFilterModel();
      if (api) this.applyUrlFilter(api, filterModel);
    });
  }

  private onGridReady(api: GridApi): void {
    this.gridApi.set(api);
    api.addEventListener('stateUpdated', (event) => this.writeFilterToUrl(event));
  }

  private applyUrlFilter(api: GridApi, filterModel: FilterModel): void {
    if (sameFilter(api.getState().filter?.filterModel, filterModel)) return;
    const otherState = (Object.keys(api.getState()) as GridStateKey[]).filter(
      (key) => !SYNCED_STATE.includes(key),
    );
    api.setState({ filter: { filterModel } }, otherState);
  }

  // replaceUrl: back steps out of the tab, not through each filter edit.
  // [tableFilterParams] relative to route => current path, its matrix params replaced
  private writeFilterToUrl(event: StateUpdatedEvent): void {
    if (!SYNCED_STATE.some((key) => event.sources.includes(key))) return;
    const filterModel = event.state.filter?.filterModel ?? {};
    if (sameFilter(filterModel, this.urlFilterModel())) return;
    void this.workbenchRouter.navigate([tableFilterParams(filterModel)], {
      relativeTo: this.route,
      target: this.view.id,
      replaceUrl: true,
    });
  }
}
