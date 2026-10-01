import { inject } from '@angular/core';
import { PermissionsService } from '@core/auth/permissions.service';
import {
  provideWorkbenchInitializer,
  WorkbenchLayout,
  WorkbenchRouter,
  WorkbenchStartup,
  WorkbenchStartupPhase,
} from '@scion/workbench';

export const ADMIN_MENU_PART = 'admin-menu';

/**
 * Adds the admin menu to the side bar for a Monteis admin and removes it for everyone else.
 *
 * The part cannot live in the static layout of `workbenchConfig`: the workbench persists the
 * layout per browser, so a layout stored during an admin session must lose the part when a
 * non-admin logs in.
 */
export function provideAdminMenuPart() {
  return provideWorkbenchInitializer(
    () => {
      const isAdmin = inject(PermissionsService).isAdmin();
      const router = inject(WorkbenchRouter);
      // Not awaited: the layout only exists once the startup, which waits for this initializer, is done.
      void inject(WorkbenchStartup).whenDone.then(() =>
        router.navigate((layout) => syncAdminMenuPart(layout, isAdmin)),
      );
    },
    { phase: WorkbenchStartupPhase.PostStartup },
  );
}

/** Returns `null` (no navigation) when the layout already matches the role. */
export function syncAdminMenuPart(
  layout: WorkbenchLayout,
  isAdmin: boolean,
): WorkbenchLayout | null {
  if (layout.hasPart(ADMIN_MENU_PART) === isAdmin) {
    return null;
  }
  if (!isAdmin) {
    return layout.removePart(ADMIN_MENU_PART);
  }
  return layout
    .addPart(
      ADMIN_MENU_PART,
      { dockTo: 'left-top' },
      { label: '%menu.admin', icon: 'admin_panel_settings' },
    )
    .navigatePart(ADMIN_MENU_PART, [ADMIN_MENU_PART]);
}
