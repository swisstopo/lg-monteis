import { Pipe, PipeTransform } from '@angular/core';
import { ExperimentOwnerDto } from '@core/generated';

export function ownerDisplayName(owner: ExperimentOwnerDto): string {
  return [owner.firstName, owner.lastName].filter((name) => !!name?.trim()).join(' ');
}

@Pipe({ name: 'ownerName' })
export class OwnerNamePipe implements PipeTransform {
  transform(owner: ExperimentOwnerDto): string {
    return ownerDisplayName(owner);
  }
}
