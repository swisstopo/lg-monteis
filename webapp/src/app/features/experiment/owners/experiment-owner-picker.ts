import { Component, computed, inject, input, model, resource } from '@angular/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectChange, MatSelectModule } from '@angular/material/select';
import { DirectoryUserDto } from '@core/generated';
import { userDirectoryErrorKey } from '@core/user-directory/user-directory-error';
import { userDisplayName, UserOptionPipe } from '@core/user-directory/user-name';
import { ExperimentService } from '@features/experiment/services/experiment.service';
import { TranslatePipe } from '@ngx-translate/core';

/** picks the owners of an experiment out of its PIs in Keycloak */
@Component({
  selector: 'app-experiment-owner-picker',
  imports: [MatFormFieldModule, MatSelectModule, TranslatePipe, UserOptionPipe],
  templateUrl: './experiment-owner-picker.html',
  styleUrl: './experiment-owner-picker.scss',
})
export class ExperimentOwnerPicker {
  private readonly experimentService = inject(ExperimentService);

  readonly experimentId = input.required<string>();
  readonly selectedOwnerIds = model<string[]>([]);
  // the stored owners, the cached read can still list one the fresh candidates no longer have
  readonly owners = input<DirectoryUserDto[]>([]);
  /** message key of a failed save */
  readonly error = input<string>();

  protected readonly candidates = resource({
    params: () => ({ id: this.experimentId() }),
    loader: ({ params }) => this.experimentService.findOwnerCandidatesForExperiment(params.id),
  });

  protected readonly candidatesErrorKey = computed(() =>
    userDirectoryErrorKey(this.candidates.error()),
  );

  // without candidates the disabled select still names the stored owners
  protected readonly selectableOwners = computed(() =>
    this.candidates.hasValue() ? this.candidates.value() : this.owners(),
  );

  // left out of the selection, the backend refuses an owner who is no PI anymore
  protected readonly formerOwners = computed(() => {
    if (!this.candidates.hasValue()) return [];
    const candidateIds = new Set(this.candidates.value().map((owner) => owner.id));
    return this.owners().filter((owner) => !candidateIds.has(owner.id));
  });

  protected readonly selectedIds = computed(() => {
    const selectableIds = new Set(this.selectableOwners().map((owner) => owner.id));
    return this.selectedOwnerIds().filter((id) => selectableIds.has(id));
  });

  protected readonly selectedNames = computed(() => {
    const selected = new Set(this.selectedOwnerIds());
    return this.selectableOwners()
      .filter((owner) => selected.has(owner.id))
      .map(userDisplayName)
      .join(', ');
  });

  protected onSelectionChange(event: MatSelectChange<string[]>): void {
    this.selectedOwnerIds.set(event.value);
  }
}
