import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DirectoryUserDto } from '@core/generated';
import { UserNamePipe } from '@core/user-directory/user-name';
import { TranslatePipe } from '@ngx-translate/core';
import { missingOwnersKey, OwnersStatus } from './owners-status';

/** the owners of an experiment with their contact details, read only */
@Component({
  selector: 'app-experiment-owner-list',
  imports: [TranslatePipe, UserNamePipe],
  templateUrl: './experiment-owner-list.html',
  styleUrl: './experiment-owner-list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExperimentOwnerList {
  readonly owners = input<DirectoryUserDto[]>([]);
  readonly status = input<OwnersStatus>();

  protected readonly missingOwnersKey = computed(() => missingOwnersKey(this.status()));
}
