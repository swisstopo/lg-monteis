import { ExperimentOwnerDto } from '@core/generated';

export function ownerDisplayName(owner: ExperimentOwnerDto): string {
  return [owner.firstName, owner.lastName].filter((name) => !!name?.trim()).join(' ');
}
