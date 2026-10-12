import { ExperimentOwnerDto } from '@core/generated';

export function ownerIdsOf(owners: ExperimentOwnerDto[] | undefined): string[] {
  return (owners ?? []).map((owner) => owner.id);
}

export function sameIds(a: string[], b: string[]): boolean {
  const set = new Set(a);
  return set.size === b.length && b.every((id) => set.has(id));
}
