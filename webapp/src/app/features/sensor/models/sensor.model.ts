import { translate } from '@ngx-translate/core';
import { SensorResponseDto } from '../../../core/generated';

export type Unit = SensorResponseDto.UnitEnum;
export type SensorType = SensorResponseDto.TypeEnum;

export const UNIT_METADATA: Record<Unit, { label: string; symbol: string }> = {
  [SensorResponseDto.UnitEnum.Seconds]: { label: translate('sensor.unit.seconds')(), symbol: 's' },
  [SensorResponseDto.UnitEnum.Meter]: { label: translate('sensor.unit.meter')(), symbol: 'm' },
  [SensorResponseDto.UnitEnum.Kilogram]: {
    label: translate('sensor.unit.kilogram')(),
    symbol: 'kg',
  },
  [SensorResponseDto.UnitEnum.Ampere]: { label: translate('sensor.unit.ampere')(), symbol: 'A' },
  [SensorResponseDto.UnitEnum.Kelvin]: { label: translate('sensor.unit.kelvin')(), symbol: 'K' },
  [SensorResponseDto.UnitEnum.Mole]: { label: translate('sensor.unit.mole')(), symbol: 'mol' },
  [SensorResponseDto.UnitEnum.Candela]: { label: translate('sensor.unit.candela')(), symbol: 'cd' },
};

export const SENSOR_TYPE_METADATA: Record<SensorType, { label: string }> = {
  [SensorResponseDto.TypeEnum.WindSpeed]: { label: translate('sensor.type.windSpeed')() },
  [SensorResponseDto.TypeEnum.StressRadial]: { label: translate('sensor.type.stressRadial')() },
  [SensorResponseDto.TypeEnum.Temperature]: { label: translate('sensor.type.temperature')() },
  [SensorResponseDto.TypeEnum.Volume]: { label: translate('sensor.type.volume')() },
  [SensorResponseDto.TypeEnum.Other]: { label: translate('sensor.type.other')() },
};
