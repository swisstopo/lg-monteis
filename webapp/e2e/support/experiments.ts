import { APIRequestContext } from '@playwright/test';
import { Experiment } from './monteis-api';
import { readJson } from './responses';

/**
 * Experiments of db/meta/seed/R__seed_dev_data.sql. alice holds "read + write" on Alpha
 * (…0301), bob holds "read" on Beta (…0302).
 */
export const SEEDED_EXPERIMENTS = {
  alpha: 'Mont Terri Alpha',
  beta: 'Mont Terri Beta',
};

const E2E_EXPERIMENT_PERIOD = { start: '2030-01-01', end: '2030-05-05' };

/** Eight random hex characters, short enough to append to any length-limited field. */
export function randomSuffix(): string {
  return crypto.randomUUID().substring(0, 8);
}

/**
 * A fresh experiment name. Experiment names are unique and these tests never delete what they
 * create, so every run and every browser project needs its own name.
 */
export function uniqueExperimentName(): string {
  return `E2E-${randomSuffix()}`;
}

export async function createExperiment(api: APIRequestContext, name: string): Promise<Experiment> {
  const response = await api.post('/api/experiments', {
    data: { name, period: E2E_EXPERIMENT_PERIOD },
  });
  return readJson(response, `Creating experiment "${name}"`);
}

export async function findExperiment(api: APIRequestContext, id: string): Promise<Experiment> {
  return readJson(await api.get(`/api/experiments/${id}`), `Reading experiment ${id}`);
}
