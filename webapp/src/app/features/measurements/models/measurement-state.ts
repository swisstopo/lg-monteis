import { MeasurementResponseDto } from '@core/generated';

type MeasurementStatus = MeasurementResponseDto.MeasurementStatusEnum;

const MEASUREMENT_STATE_CLASS: Record<MeasurementStatus, string> = {
  [MeasurementResponseDto.MeasurementStatusEnum.Correct]: '',
  [MeasurementResponseDto.MeasurementStatusEnum.TooLow]: 'measurement-state-alarm',
  [MeasurementResponseDto.MeasurementStatusEnum.TooHigh]: 'measurement-state-alarm',
};

export function measurementStateClass(status: MeasurementStatus | undefined): string {
  return status ? MEASUREMENT_STATE_CLASS[status] : '';
}
