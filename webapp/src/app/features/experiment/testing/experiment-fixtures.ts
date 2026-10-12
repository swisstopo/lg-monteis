import { ExperimentOwnerDto, ExperimentResponseDto } from '@core/generated';

export const ALICE: ExperimentOwnerDto = {
  id: 'alice',
  firstName: 'Alice',
  lastName: 'Muster',
  email: 'alice@example.com',
};

export const BOB: ExperimentOwnerDto = {
  id: 'bob',
  firstName: 'Bob',
  lastName: 'Beispiel',
  email: 'bob@example.com',
};

export function anExperiment(
  overrides: Partial<ExperimentResponseDto> = {},
): ExperimentResponseDto {
  return {
    id: 'experiment-1',
    version: 3,
    name: 'Mont Terri Alpha',
    comment: 'borehole',
    period: { start: '2030-01-01', end: '2030-05-05' },
    owners: [ALICE],
    ownersStatus: 'SHOWN',
    ...overrides,
  };
}
