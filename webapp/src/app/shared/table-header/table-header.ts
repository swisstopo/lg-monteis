import { Component, computed, inject, input, inputBinding, Type } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { MatFormField, MatInput, MatPrefix } from '@angular/material/input';
import { TranslatePipe } from '@ngx-translate/core';

@Component({
  selector: 'app-table-header',
  standalone: true,
  imports: [MatButton, MatIcon, MatFormField, MatInput, MatPrefix, TranslatePipe],
  templateUrl: './table-header.html',
  styleUrl: './table-header.scss',
})
export class TableHeader {
  private readonly dialog = inject(MatDialog);

  entityName = input.required<string>();

  selectedItemId = input<number | undefined>(undefined);
  dialogComponent = input<Type<any>>();

  showCreate = input<boolean>(false);
  showEdit = input<boolean>(false);
  showDownload = input<boolean>(false);
  searchAction = input<(term: string) => void>();

  customIdBinding = input<string>();

  translationPrefix = computed(() => `${this.entityName()}.tableHeader`);

  computedIdName = computed(() => {
    return this.customIdBinding() ?? `${this.entityName()}Id`;
  });

  onSearch(event: Event): void {
    const inputElement = event.target as HTMLInputElement;
    this.searchAction()?.(inputElement.value);
  }

  onCreate(): void {
    const comp = this.dialogComponent();
    if (!comp) return;

    this.dialog.open(comp, { width: '60vw', maxWidth: '1200px', autoFocus: true });
  }

  onEdit(): void {
    const comp = this.dialogComponent();
    const objectId = this.selectedItemId();

    if (!comp || objectId === undefined) return;

    this.dialog.open(comp, {
      width: '60vw',
      maxWidth: '1200px',
      autoFocus: true,
      bindings: [inputBinding(this.computedIdName(), () => objectId)],
    });
  }

  onDownload(): void {
    // Not implemented yet.
  }
}
