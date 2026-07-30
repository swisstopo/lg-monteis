import { SensorResponseDto } from '../../../core/generated';

export type Unit = SensorResponseDto.UnitEnum;
export type SensorType = SensorResponseDto.TypeEnum;

export const UNIT_METADATA: Record<Unit, { labelKey: string; symbol: string }> = {
  [SensorResponseDto.UnitEnum.Seconds]: { labelKey: 'sensor.unit.seconds', symbol: 's' },
  [SensorResponseDto.UnitEnum.Meter]: { labelKey: 'sensor.unit.meter', symbol: 'm' },
  [SensorResponseDto.UnitEnum.Kilogram]: { labelKey: 'sensor.unit.kilogram', symbol: 'kg' },
  [SensorResponseDto.UnitEnum.Ampere]: { labelKey: 'sensor.unit.ampere', symbol: 'A' },
  [SensorResponseDto.UnitEnum.Kelvin]: { labelKey: 'sensor.unit.kelvin', symbol: 'K' },
  [SensorResponseDto.UnitEnum.Mole]: { labelKey: 'sensor.unit.mole', symbol: 'mol' },
  [SensorResponseDto.UnitEnum.Candela]: { labelKey: 'sensor.unit.candela', symbol: 'cd' },
};

export const SENSOR_TYPE_METADATA: Record<SensorType, { labelKey: string }> = {
  [SensorResponseDto.TypeEnum.WindSpeed]: { labelKey: 'sensor.type.windSpeed' },
  [SensorResponseDto.TypeEnum.StressRadial]: { labelKey: 'sensor.type.stressRadial' },
  [SensorResponseDto.TypeEnum.Temperature]: { labelKey: 'sensor.type.temperature' },
  [SensorResponseDto.TypeEnum.Volume]: { labelKey: 'sensor.type.volume' },
  [SensorResponseDto.TypeEnum.Other]: { labelKey: 'sensor.type.other' },
};
