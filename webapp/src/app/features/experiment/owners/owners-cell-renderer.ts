import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { MatTooltip } from '@angular/material/tooltip';
import { ExperimentOwnerDto } from '@core/generated';
import { ICellRendererAngularComp } from 'ag-grid-angular';
import { ICellRendererParams } from 'ag-grid-community';
import { ownerDisplayName } from './owner-name';

@Component({
  selector: 'app-owners-cell-renderer',
  templateUrl: './owners-cell-renderer.html',
  styleUrl: './owners-cell-renderer.scss',
  imports: [MatTooltip],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OwnersCellRenderer implements ICellRendererAngularComp {
  protected readonly owners = signal<ExperimentOwnerDto[]>([]);
  protected readonly displayName = ownerDisplayName;

  agInit(params: ICellRendererParams<unknown, ExperimentOwnerDto[]>): void {
    this.owners.set(params.value ?? []);
  }

  refresh(params: ICellRendererParams<unknown, ExperimentOwnerDto[]>): boolean {
    this.owners.set(params.value ?? []);
    return true;
  }

  protected contactOf(owner: ExperimentOwnerDto): string {
    return [ownerDisplayName(owner), owner.email].filter((part) => !!part).join('\n');
  }
}
