import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { MatTooltip } from '@angular/material/tooltip';
import { ExperimentOwnerDto, ExperimentResponseDto } from '@core/generated';
import { TranslatePipe } from '@ngx-translate/core';
import { ICellRendererAngularComp } from 'ag-grid-angular';
import { ICellRendererParams } from 'ag-grid-community';
import { ownerDisplayName } from './owner-name';
import { missingOwnersKey } from './owners-status';

@Component({
  selector: 'app-owners-cell-renderer',
  templateUrl: './owners-cell-renderer.html',
  styleUrl: './owners-cell-renderer.scss',
  imports: [MatTooltip, TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OwnersCellRenderer implements ICellRendererAngularComp {
  protected readonly owners = signal<ExperimentOwnerDto[]>([]);
  protected readonly missingOwnersKey = signal<string | undefined>(undefined);
  protected readonly displayName = ownerDisplayName;

  agInit(params: ICellRendererParams<ExperimentResponseDto, ExperimentOwnerDto[]>): void {
    this.refresh(params);
  }

  refresh(params: ICellRendererParams<ExperimentResponseDto, ExperimentOwnerDto[]>): boolean {
    this.owners.set(params.value ?? []);
    this.missingOwnersKey.set(missingOwnersKey(params.data?.ownersStatus));
    return true;
  }
}
