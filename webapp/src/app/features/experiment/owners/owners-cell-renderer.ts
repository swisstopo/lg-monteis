import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { MatTooltip } from '@angular/material/tooltip';
import { DirectoryUserDto, ExperimentResponseDto } from '@core/generated';
import { UserNamePipe } from '@core/user-directory/user-name';
import { TranslatePipe } from '@ngx-translate/core';
import { ICellRendererAngularComp } from 'ag-grid-angular';
import { ICellRendererParams } from 'ag-grid-community';
import { missingOwnersKey } from './owners-status';

@Component({
  selector: 'app-owners-cell-renderer',
  templateUrl: './owners-cell-renderer.html',
  styleUrl: './owners-cell-renderer.scss',
  imports: [MatTooltip, TranslatePipe, UserNamePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OwnersCellRenderer implements ICellRendererAngularComp {
  protected readonly owners = signal<DirectoryUserDto[]>([]);
  protected readonly missingOwnersKey = signal<string | undefined>(undefined);

  agInit(params: ICellRendererParams<ExperimentResponseDto, DirectoryUserDto[]>): void {
    this.refresh(params);
  }

  refresh(params: ICellRendererParams<ExperimentResponseDto, DirectoryUserDto[]>): boolean {
    this.owners.set(params.value ?? []);
    this.missingOwnersKey.set(missingOwnersKey(params.data?.ownersStatus));
    return true;
  }
}
