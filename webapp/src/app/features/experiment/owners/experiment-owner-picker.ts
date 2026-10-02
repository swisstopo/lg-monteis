import { Component, computed, inject, input, model, resource, signal } from '@angular/core';
import {
  MatAutocompleteModule,
  MatAutocompleteSelectedEvent,
} from '@angular/material/autocomplete';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatInput } from '@angular/material/input';
import { ExperimentOwnerDto } from '@core/generated';
import { ExperimentService } from '@features/experiment/services/experiment.service';
import { TranslatePipe } from '@ngx-translate/core';
import { ownerDisplayName } from './owner-name';

/** Picks the owners of an experiment out of its PIs in Keycloak. */
@Component({
  selector: 'app-experiment-owner-picker',
  imports: [
    MatFormFieldModule,
    MatChipsModule,
    MatAutocompleteModule,
    MatIcon,
    MatInput,
    TranslatePipe,
  ],
  templateUrl: './experiment-owner-picker.html',
})
export class ExperimentOwnerPicker {
  private readonly experimentService = inject(ExperimentService);

  readonly experimentId = input.required<string>();
  readonly selectedOwnerIds = model<string[]>([]);

  protected readonly displayName = ownerDisplayName;
  protected readonly searchText = signal('');

  protected readonly candidates = resource({
    params: () => ({ id: this.experimentId() }),
    loader: ({ params }) => this.experimentService.getOwnerCandidates(params.id),
  });

  protected readonly selectedOwners = computed(() => {
    const byId = new Map((this.candidates.value() ?? []).map((owner) => [owner.id, owner]));
    return this.selectedOwnerIds()
      .map((id) => byId.get(id))
      .filter((owner): owner is ExperimentOwnerDto => owner !== undefined);
  });

  protected readonly selectableOwners = computed(() => {
    const selected = new Set(this.selectedOwnerIds());
    const search = this.searchText().trim().toLowerCase();
    return (this.candidates.value() ?? []).filter(
      (owner) =>
        !selected.has(owner.id!) &&
        (!search ||
          ownerDisplayName(owner).toLowerCase().includes(search) ||
          owner.email?.toLowerCase().includes(search)),
    );
  });

  protected onSelected(event: MatAutocompleteSelectedEvent, input: HTMLInputElement): void {
    const id = event.option.value as string;
    this.selectedOwnerIds.update((ids) => (ids.includes(id) ? ids : [...ids, id]));
    input.value = '';
    this.searchText.set('');
  }

  protected remove(owner: ExperimentOwnerDto): void {
    this.selectedOwnerIds.update((ids) => ids.filter((id) => id !== owner.id));
  }
}
