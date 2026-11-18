import { MeasurementResponseDto } from '@core/generated';

type NamedSensorParameter = Pick<
  MeasurementResponseDto,
  'sensorName' | 'sensorParameterName' | 'dasKey'
>;

export function sensorParameterLabel(parameter: NamedSensorParameter): string {
  return parameter.sensorParameterName
    ? `${parameter.sensorName} – ${parameter.sensorParameterName}`
    : (parameter.dasKey ?? '');
}
