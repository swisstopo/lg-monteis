import { SensorResponseDto } from '../../../core/generated';

export type Unit = SensorResponseDto.UnitEnum;

export const UNIT_METADATA: Record<Unit, { labelKey: string; symbol: string }> = {
  [SensorResponseDto.UnitEnum.Seconds]: {
    labelKey: 'sensor.unit.seconds.label',
    symbol: 'sensor.unit.seconds.symbol',
  },
  [SensorResponseDto.UnitEnum.Meter]: {
    labelKey: 'sensor.unit.meter.label',
    symbol: 'sensor.unit.meter.symbol',
  },
  [SensorResponseDto.UnitEnum.Kilogram]: {
    labelKey: 'sensor.unit.kilogram.label',
    symbol: 'sensor.unit.kilogram.symbol',
  },
  [SensorResponseDto.UnitEnum.Ampere]: {
    labelKey: 'sensor.unit.ampere.label',
    symbol: 'sensor.unit.ampere.symbol',
  },
  [SensorResponseDto.UnitEnum.Kelvin]: {
    labelKey: 'sensor.unit.kelvin.label',
    symbol: 'sensor.unit.kelvin.symbol',
  },
  [SensorResponseDto.UnitEnum.Mole]: {
    labelKey: 'sensor.unit.mole.label',
    symbol: 'sensor.unit.mole.symbol',
  },
  [SensorResponseDto.UnitEnum.Candela]: {
    labelKey: 'sensor.unit.candela.label',
    symbol: 'sensor.unit.candela.symbol',
  },
};
