import { inject } from '@angular/core';
import { ExperimentService } from '@features/experiment/services/experiment.service';
import { WorkbenchLayout } from '@scion/workbench';

const MONITORING_MENU = 'monitoring-menu';

// injects: call from a layout function, it runs in an injection context
export async function addMonitoringMenu(layout: WorkbenchLayout): Promise<WorkbenchLayout> {
  // loading fails => no part, a rejected layout function would stop the workbench from starting
  const experiments = await inject(ExperimentService)
    .findWritableExperiments()
    .catch((error: unknown) => {
      console.error('Monitoring menu left out: writable experiments failed to load', error);
      return [];
    });
  if (experiments.length === 0) {
    return layout;
  }
  return layout
    .addPart(
      MONITORING_MENU,
      { dockTo: 'left-top' },
      { label: '%menu.monitoring', icon: 'notifications' },
    )
    .navigatePart(MONITORING_MENU, [MONITORING_MENU]);
}
