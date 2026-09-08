import { ChangeDetectionStrategy, Component, inject, output } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { MatFormField, MatInput, MatPrefix } from '@angular/material/input';
import { PermissionsService } from '@core/auth/permissions.service';
import { TranslatePipe } from '@ngx-translate/core';

@Component({
  selector: 'app-table-header',
  standalone: true,
  imports: [MatIcon, MatFormField, MatInput, MatPrefix, TranslatePipe],
  templateUrl: './table-header.html',
  styleUrl: './table-header.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TableHeader {
  protected readonly permissions = inject(PermissionsService);

  translationPrefix = `tableHeader`;

  searchAction = output<string>();

  onSearch(event: Event): void {
    const inputElement = event.target as HTMLInputElement;
    this.searchAction.emit(inputElement.value);
  }
}
