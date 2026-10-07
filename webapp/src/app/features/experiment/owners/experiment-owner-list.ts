import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ExperimentOwnerDto } from '@core/generated';
import { TranslatePipe } from '@ngx-translate/core';
import { ownerDisplayName } from './owner-name';

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
  readonly unavailable = input(false);

  protected readonly displayName = ownerDisplayName;
}
