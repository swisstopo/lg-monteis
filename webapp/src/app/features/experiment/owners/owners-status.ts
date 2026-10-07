import { ExperimentResponseDto } from '@core/generated';

export type OwnersStatus = ExperimentResponseDto.OwnersStatusEnum;

/** Translation key that says why the owners are missing, none when they are shown. */
export function missingOwnersKey(status: OwnersStatus | undefined): string | undefined {
  return status && status !== ExperimentResponseDto.OwnersStatusEnum.Shown
    ? `experiment.owners.status.${status}`
    : undefined;
}
