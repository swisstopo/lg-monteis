import { inject, Signal } from '@angular/core';
import { WriteSensorDto, WriteSensorParameterDto } from '@core/generated';
import { TranslateService } from '@ngx-translate/core';

export type Unit = WriteSensorParameterDto.UnitEnum;

const UNIT_KEYS: Record<Unit, { labelKey: string; symbolKey: string }> = {
  [WriteSensorParameterDto.UnitEnum.Seconds]: {
    labelKey: 'sensor.unit.option.seconds.label',
    symbolKey: 'sensor.unit.option.seconds.symbol',
  },
  [WriteSensorParameterDto.UnitEnum.Meter]: {
    labelKey: 'sensor.unit.option.meter.label',
    symbolKey: 'sensor.unit.option.meter.symbol',
  },
  [WriteSensorParameterDto.UnitEnum.Kilogram]: {
    labelKey: 'sensor.unit.option.kilogram.label',
    symbolKey: 'sensor.unit.option.kilogram.symbol',
  },
  [WriteSensorParameterDto.UnitEnum.Ampere]: {
    labelKey: 'sensor.unit.option.ampere.label',
    symbolKey: 'sensor.unit.option.ampere.symbol',
  },
  [WriteSensorParameterDto.UnitEnum.Kelvin]: {
    labelKey: 'sensor.unit.option.kelvin.label',
    symbolKey: 'sensor.unit.option.kelvin.symbol',
  },
  [WriteSensorParameterDto.UnitEnum.Mole]: {
    labelKey: 'sensor.unit.option.mole.label',
    symbolKey: 'sensor.unit.option.mole.symbol',
  },
  [WriteSensorParameterDto.UnitEnum.Candela]: {
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

export type Das = WriteSensorDto.DasEnum;

const DAS_KEYS: Record<Das, { labelKey: string }> = {
  [WriteSensorDto.DasEnum.SolExperts]: { labelKey: 'sensor.das.option.solExperts.label' },
};

export function getDasMetadata(): Record<Das, { label: Signal<string> }> {
  const translateService = inject(TranslateService);
  return Object.fromEntries(
    Object.entries(DAS_KEYS).map(([das, { labelKey }]) => [
      das,
      { label: translateService.translate(labelKey) },
    ]),
  ) as Record<Das, { label: Signal<string> }>;
}
