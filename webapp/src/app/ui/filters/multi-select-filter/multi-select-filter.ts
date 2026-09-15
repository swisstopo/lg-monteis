import { Component, computed, ElementRef, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { IFilterAngularComp } from 'ag-grid-angular';
import { IDoesFilterPassParams, IFilterParams } from 'ag-grid-community';

import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxChange, MatCheckboxModule } from '@angular/material/checkbox';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

interface SetFilterModel {
  filterType: 'set';
  values: (string | null)[];
}

interface FilterOption {
  displayName: string;
  value: string | null;
}

interface MultiSelectFilterParams extends IFilterParams {
  valuesProvider: () => Promise<FilterOption[]>;
  /**
   * When set, a pseudo-option with this label and a `null` value is appended to the options
   * returned by valuesProvider, letting the user filter for rows where this column is blank
   * (e.g. "No Main Experiment"). Only meaningful for nullable columns.
   */
  blankOptionLabel?: string;
}

@Component({
  selector: 'app-multi-select-filter',
  imports: [
    FormsModule,
    MatMenuModule,
    MatButtonModule,
    MatCheckboxModule,
    MatIconModule,
    MatDividerModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    TranslatePipe,
  ],
  templateUrl: './multi-select-filter.html',
  styleUrls: ['./multi-select-filter.scss'],
})
export class MultiSelectFilter implements IFilterAngularComp {
  readonly filterOptionsContainer = viewChild<ElementRef<HTMLDivElement>>('filterOptions');

  private params!: MultiSelectFilterParams;
  private initialValues = new Set<string | null>();

  readonly isLoading = signal(true);
  readonly hasError = signal(false);
  readonly showFilter = signal(false);
  searchText = signal('');
  readonly filterOptions = signal<FilterOption[]>([]);
  readonly selectedValues = signal<Set<string | null>>(new Set());

  readonly filteredOptions = computed(() => {
    const searchString = this.searchText().toLowerCase();
    const options = this.filterOptions();

    if (!searchString) return options;
    return options.filter((option) => option.displayName.toLowerCase().includes(searchString));
  });

  readonly isAllSelected = computed(() => {
    const optionsCount = this.filterOptions().length;
    return optionsCount > 0 && this.selectedValues().size === optionsCount;
  });

  readonly isIndeterminate = computed(() => {
    const size = this.selectedValues().size;
    return size > 0 && !this.isAllSelected();
  });

  agInit(params: MultiSelectFilterParams): void {
    this.params = params;
    this.hasError.set(false);

    // Check for the required function
    if (typeof params.valuesProvider !== 'function') {
      console.error(
        "AG Grid Filter Error: 'valuesProvider' function not supplied in filterParams.",
      );
      this.hasError.set(true);
      return;
    }

    this.isLoading.set(true);

    // Call the function to get the data
    params
      .valuesProvider()
      .then((options) => {
        const blankOptionLabel = params.blankOptionLabel;
        this.filterOptions.set(
          blankOptionLabel
            ? [...(options || []), { displayName: blankOptionLabel, value: null }]
            : options || [],
        );
      })
      .catch((error) => {
        console.error("AG Grid Filter Error: 'valuesProvider' failed.", error);
        this.hasError.set(true);
      })
      .finally(() => {
        this.isLoading.set(false);
        // Check if the scrollbar is visible AFTER the options have been rendered.
        this.clearAndCheckFilter();
      });
  }

  public clearAndCheckFilter(): void {
    setTimeout(() => {
      const el = this.filterOptionsContainer()?.nativeElement;
      if (el) {
        // Check if a vertical scrollbar is present
        this.showFilter.set(el.scrollHeight > el.clientHeight);
      }
    });
  }

  isFilterActive(): boolean {
    return this.selectedValues().size > 0;
  }

  doesFilterPass(params: IDoesFilterPassParams): boolean {
    const selected = this.selectedValues();

    const value = this.params.getValue(params.node);
    return selected.has(value ?? null);
  }

  getModel(): SetFilterModel | null {
    const selected = this.selectedValues();
    if (selected.size === 0) return null;

    return {
      filterType: 'set',
      values: Array.from(selected),
    };
  }

  setModel(model: SetFilterModel | null): void {
    const newSet = new Set<string | null>();

    if (model?.values) {
      model.values.forEach((value) => newSet.add(value));
    }

    this.selectedValues.set(newSet);
    this.initialValues = new Set(newSet);
  }

  private closePopup(): void {
    this.searchText.set('');
    this.params.api.hidePopupMenu();
  }

  private restoreToInitialState(): void {
    this.selectedValues.set(new Set(this.initialValues));
  }

  onApplyClick(): void {
    this.params.filterChangedCallback();
    this.initialValues = new Set(this.selectedValues());
    this.closePopup();
  }

  onCancelClick(): void {
    this.restoreToInitialState();
    this.closePopup();
  }

  afterGuiAttached(): void {
    this.restoreToInitialState();
    this.searchText.set('');
    this.clearAndCheckFilter();
  }

  onValueChanged(value: string | null, event: MatCheckboxChange) {
    this.selectedValues.update((currentSet) => {
      const newSet = new Set(currentSet);
      if (event.checked) {
        newSet.add(value);
      } else {
        newSet.delete(value);
      }
      return newSet;
    });
  }

  onAllChanged(event: MatCheckboxChange): void {
    if (event.checked) {
      const allValues = this.filterOptions().map((o) => o.value);
      this.selectedValues.set(new Set(allValues));
    } else {
      this.selectedValues.set(new Set());
    }
  }
}
