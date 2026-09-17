import { Component, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { translate } from '@ngx-translate/core';
import { IFloatingFilterAngularComp } from 'ag-grid-angular';
import { IFloatingFilterParams, TextFilterModel } from 'ag-grid-community';
import { debounceTime, Subject } from 'rxjs';

/**
 * Number of characters a column search needs before it is sent to the backend. Below it the
 * column is treated as unsearched, so a half-typed term never pages the whole table in.
 */
export const MIN_SEARCH_CHARS = 3;

/** Idle time after the last keystroke before the search is applied. */
const DEBOUNCE_MS = 300;

/**
 * Floating filter for a column searched as free text: it applies a `contains` search as the user
 * types, but only from {@link MIN_SEARCH_CHARS} characters on. Pair it with
 * `filter: 'agTextColumnFilter'`, whose popup stays available and unchanged.
 *
 * ag-grid's own text floating filter applies every keystroke, which on a server-side table means
 * a page request plus a count query per character - and the first one or two characters match
 * nearly everything anyway.
 */
@Component({
  selector: 'app-min-chars-text-filter',
  templateUrl: './min-chars-text-filter.html',
  styleUrl: './min-chars-text-filter.scss',
})
export class MinCharsTextFilter implements IFloatingFilterAngularComp {
  private params!: IFloatingFilterParams;
  private readonly typed = new Subject<string>();

  protected readonly value = signal('');
  protected readonly placeholder = translate('common.search');

  constructor() {
    this.typed.pipe(debounceTime(DEBOUNCE_MS), takeUntilDestroyed()).subscribe((text) => {
      this.applySearch(text);
    });
  }

  agInit(params: IFloatingFilterParams): void {
    this.params = params;
  }

  /** Keeps the input in sync when the model changes elsewhere, e.g. via the filter popup's Clear. */
  onParentModelChanged(model: TextFilterModel | null): void {
    this.value.set(model?.filter ?? '');
  }

  protected onInput(event: Event): void {
    const text = (event.target as HTMLInputElement).value;
    this.value.set(text);
    this.typed.next(text);
  }

  private applySearch(text: string): void {
    const trimmed = text.trim();
    const search = trimmed.length >= MIN_SEARCH_CHARS ? trimmed : null;
    const applied = (this.params.currentParentModel() as TextFilterModel | null)?.filter ?? null;

    // Nothing reloads while the term is too short, and shortening an applied term back below the
    // threshold clears it - otherwise the table would keep showing results the input no longer
    // spells out.
    if (search === applied) return;

    this.params.parentFilterInstance((instance) => {
      instance.onFloatingFilterChanged('contains', search);
    });
  }
}
