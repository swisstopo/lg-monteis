import { APIRequestContext, expect, Page } from '@playwright/test';
import { hasPath, readJson } from './responses';

const FULCRUM_DOCUMENTS = '/api/fulcrum-documents';
const FULCRUM_SYNC = '/api/fulcrum-sync';
const ALREADY_RUNNING = 409;

/**
 * The files the Fulcrum stub of core serves (core/src/test/.../itconfig/FulcrumStubConfiguration),
 * one per literature row. The niche monitoring row lists an appendix as its second attachment,
 * the sync only takes the first.
 */
export const STUB_FILES = {
  opalinusReport: 'e2e-opalinus-report.pdf',
  gasMeasurements: 'e2e-gas-measurements.xlsx',
  nicheMonitoring: 'e2e-niche-monitoring.pdf',
  nicheMonitoringAppendix: 'e2e-niche-monitoring-appendix.pdf',
};

/**
 * Starts a sync, or joins the one already running, and waits until no sync runs any more, so the
 * Fulcrum Documents of the stub are stored.
 */
export async function ensureFulcrumSynced(api: APIRequestContext): Promise<void> {
  const started = await api.post(FULCRUM_SYNC);
  if (!started.ok() && started.status() !== ALREADY_RUNNING) {
    throw new Error(
      `Starting the Fulcrum sync failed: ${started.status()} ${await started.text()}`,
    );
  }
  await waitForFulcrumSyncToEnd(api);
}

export async function waitForFulcrumSyncToEnd(api: APIRequestContext): Promise<void> {
  await expect
    .poll(
      async () =>
        (await readJson<{ running: boolean }>(await api.get(FULCRUM_SYNC), 'Fulcrum sync status'))
          .running,
      { timeout: 60_000 },
    )
    .toBe(false);
}

/** Opens the Fulcrum Documents table through the knowledge menu and waits for its documents. */
export async function openFulcrumDocumentTable(page: Page): Promise<void> {
  const documents = page.waitForResponse((response) => hasPath(response, FULCRUM_DOCUMENTS));
  await page.getByTitle('Knowledge').click();
  await page.getByRole('link', { name: 'Fulcrum Documents' }).click();
  expect((await documents).ok()).toBe(true);
}
