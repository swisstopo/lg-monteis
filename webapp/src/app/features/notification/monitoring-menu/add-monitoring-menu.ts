import { inject } from '@angular/core';
import { PermissionsService } from '@core/auth/permissions.service';
import { WorkbenchLayout } from '@scion/workbench';
import { firstValueFrom } from 'rxjs';

const MONITORING_MENU = 'monitoring-menu';

// injects: call from a layout function, it runs in an injection context
// TODO: runs once per app load, write access gained or lost meanwhile shows only after a reload
export async function addMonitoringMenu(layout: WorkbenchLayout): Promise<WorkbenchLayout> {
  const permissions = inject(PermissionsService);
  // signal holds the fail-closed default until /api/me answered
  await firstValueFrom(permissions.currentUser$);
  if (!permissions.hasAnyExperimentWriteAccess()) {
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
