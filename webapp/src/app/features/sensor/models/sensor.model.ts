import { inject, Signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { SensorResponseDto } from '../../../core/generated';

export type Unit = SensorResponseDto.UnitEnum;

const UNIT_KEYS: Record<Unit, { labelKey: string; symbolKey: string }> = {
  [SensorResponseDto.UnitEnum.Seconds]: {
    labelKey: 'sensor.unit.option.seconds.label',
    symbolKey: 'sensor.unit.option.seconds.symbol',
  },
  [SensorResponseDto.UnitEnum.Meter]: {
    labelKey: 'sensor.unit.option.meter.label',
    symbolKey: 'sensor.unit.option.meter.symbol',
  },
  [SensorResponseDto.UnitEnum.Kilogram]: {
    labelKey: 'sensor.unit.option.kilogram.label',
    symbolKey: 'sensor.unit.option.kilogram.symbol',
  },
  [SensorResponseDto.UnitEnum.Ampere]: {
    labelKey: 'sensor.unit.option.ampere.label',
    symbolKey: 'sensor.unit.option.ampere.symbol',
  },
  [SensorResponseDto.UnitEnum.Kelvin]: {
    labelKey: 'sensor.unit.option.kelvin.label',
    symbolKey: 'sensor.unit.option.kelvin.symbol',
  },
  [SensorResponseDto.UnitEnum.Mole]: {
    labelKey: 'sensor.unit.option.mole.label',
    symbolKey: 'sensor.unit.option.mole.symbol',
  },
  [SensorResponseDto.UnitEnum.Candela]: {
    labelKey: 'sensor.unit.option.candela.label',
    symbolKey: 'sensor.unit.option.candela.symbol',
  },
};

export function getUnitMetadata(): Record<Unit, { label: Signal<string>; symbol: Signal<string> }> {
  const translateService = inject(TranslateService);
  return Object.fromEntries(
    Object.entries(UNIT_KEYS).map(([unit, { labelKey, symbolKey }]) => [
      unit,
      {
        label: translateService.translate(labelKey),
        symbol: translateService.translate(symbolKey),
      },
    ]),
  ) as Record<Unit, { label: Signal<string>; symbol: Signal<string> }>;
}
