import { SensorResponseDto } from '../../../core/generated';

export type Unit = SensorResponseDto.UnitEnum;

export const UNIT_METADATA: Record<Unit, { labelKey: string; symbol: string }> = {
  [SensorResponseDto.UnitEnum.Seconds]: {
    labelKey: 'sensor.unit.option.seconds.label',
    symbol: 'sensor.unit.option.seconds.symbol',
  },
  [SensorResponseDto.UnitEnum.Meter]: {
    labelKey: 'sensor.unit.option.meter.label',
    symbol: 'sensor.unit.option.meter.symbol',
  },
  [SensorResponseDto.UnitEnum.Kilogram]: {
    labelKey: 'sensor.unit.option.kilogram.label',
    symbol: 'sensor.unit.option.kilogram.symbol',
  },
  [SensorResponseDto.UnitEnum.Ampere]: {
    labelKey: 'sensor.unit.option.ampere.label',
    symbol: 'sensor.unit.option.ampere.symbol',
  },
  [SensorResponseDto.UnitEnum.Kelvin]: {
    labelKey: 'sensor.unit.option.kelvin.label',
    symbol: 'sensor.unit.option.kelvin.symbol',
  },
  [SensorResponseDto.UnitEnum.Mole]: {
    labelKey: 'sensor.unit.option.mole.label',
    symbol: 'sensor.unit.option.mole.symbol',
  },
  [SensorResponseDto.UnitEnum.Candela]: {
    labelKey: 'sensor.unit.option.candela.label',
    symbol: 'sensor.unit.option.candela.symbol',
  },
};
