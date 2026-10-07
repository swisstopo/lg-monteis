import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ExperimentOwnerDto } from '@core/generated';
import { TranslatePipe } from '@ngx-translate/core';
import { ownerDisplayName } from './owner-name';
import { missingOwnersKey, OwnersStatus } from './owners-status';

/** The owners of an experiment with their contact details, read only. */
@Component({
  selector: 'app-experiment-owner-list',
  imports: [TranslatePipe],
  templateUrl: './experiment-owner-list.html',
  styleUrl: './experiment-owner-list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExperimentOwnerList {
  readonly owners = input<ExperimentOwnerDto[]>([]);
  readonly status = input<OwnersStatus>();

  protected readonly missingOwnersKey = computed(() => missingOwnersKey(this.status()));
  protected readonly displayName = ownerDisplayName;
}
