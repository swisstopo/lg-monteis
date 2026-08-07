import { Component, computed, input, output } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { MatFormField, MatInput, MatPrefix } from '@angular/material/input';
import { TranslatePipe } from '@ngx-translate/core';

@Component({
  selector: 'app-table-header',
  standalone: true,
  imports: [MatIcon, MatFormField, MatInput, MatPrefix, TranslatePipe],
  templateUrl: './table-header.html',
  styleUrl: './table-header.scss',
})
export class TableHeader {
  entityName = input.required<string>();

  translationPrefix = computed(() => `${this.entityName()}.tableHeader`);

  search = output<string>();

  onSearch(event: Event): void {
    const inputElement = event.target as HTMLInputElement;
    this.search.emit(inputElement.value);
  }
}
