import { inject, Signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { ExperimentResponseDto } from '../../../core/generated';

export type Status = ExperimentResponseDto.StatusEnum;

const STATUS_KEYS: Record<Status, { labelKey: string; symbolKey: string }> = {
  [ExperimentResponseDto.StatusEnum.Active]: {
    labelKey: 'experiment.status.option.active.label',
    symbolKey: 'experiment.status.option.active.symbol',
  },
  [ExperimentResponseDto.StatusEnum.Historic]: {
    labelKey: 'experiment.status.option.historic.label',
    symbolKey: 'experiment.status.option.historic.symbol',
  },
};

export function getStatusMetadata(): Record<
  Status,
  { label: Signal<string>; symbol: Signal<string> }
> {
  const i18n = inject(TranslateService);
  return Object.fromEntries(
    Object.entries(STATUS_KEYS).map(([status, { labelKey, symbolKey }]) => [
      status,
      { label: i18n.translate(labelKey), symbol: i18n.translate(symbolKey) },
    ]),
  ) as Record<Status, { label: Signal<string>; symbol: Signal<string> }>;
}
