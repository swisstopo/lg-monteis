import { SensorResponseDto } from '../../../core/generated';

export type Unit = SensorResponseDto.UnitEnum;
export type SensorType = SensorResponseDto.TypeEnum;

export const UNIT_METADATA: Record<Unit, { label: string; symbol: string }> = {
  [SensorResponseDto.UnitEnum.Seconds]: { label: 'Seconds', symbol: 's' },
  [SensorResponseDto.UnitEnum.Meter]: { label: 'Meter', symbol: 'm' },
  [SensorResponseDto.UnitEnum.Kilogram]: { label: 'Kilogram', symbol: 'kg' },
  [SensorResponseDto.UnitEnum.Ampere]: { label: 'Ampere', symbol: 'A' },
  [SensorResponseDto.UnitEnum.Kelvin]: { label: 'Kelvin', symbol: 'K' },
  [SensorResponseDto.UnitEnum.Mole]: { label: 'Mole', symbol: 'mol' },
  [SensorResponseDto.UnitEnum.Candela]: { label: 'Candela', symbol: 'cd' },
};

export const SENSOR_TYPE_METADATA: Record<SensorType, { label: string }> = {
  [SensorResponseDto.TypeEnum.WindSpeed]: { label: 'Wind Speed' },
  [SensorResponseDto.TypeEnum.StressRadial]: { label: 'Stress Radial' },
  [SensorResponseDto.TypeEnum.Temperature]: { label: 'Temperature' },
  [SensorResponseDto.TypeEnum.Volume]: { label: 'Volume' },
  [SensorResponseDto.TypeEnum.Other]: { label: 'Other' },
};
