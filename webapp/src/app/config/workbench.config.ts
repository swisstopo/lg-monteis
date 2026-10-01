import { inject } from '@angular/core';
import { PermissionsService } from '@core/auth/permissions.service';
import { appIconProvider } from '@core/workbench/icon-provider';
import { translate } from '@ngx-translate/core';
import { MAIN_AREA, provideWorkbench, WorkbenchLayoutFactory } from '@scion/workbench';
import { firstValueFrom, map } from 'rxjs';

const baseLayout = (factory: WorkbenchLayoutFactory) =>
  factory
    .addPart(MAIN_AREA)
    .navigatePart(MAIN_AREA, ['measurements-overview'])
    .addPart(
      'measurements-menu',
      { dockTo: 'left-top' },
      { label: '%menu.measurements', icon: 'app.measurements' },
    )
    .navigatePart('measurements-menu', ['measurements-menu'])
    .activatePart('measurements-menu')
    .addPart('setup-menu', { dockTo: 'left-top' }, { label: '%menu.setup', icon: 'settings' })
    .navigatePart('setup-menu', ['setup-menu'])
    .activatePart('setup-menu');

const adminLayout = (factory: WorkbenchLayoutFactory) =>
  baseLayout(factory)
    .addPart(
      'admin-menu',
      { dockTo: 'left-top' },
      { label: '%menu.admin', icon: 'admin_panel_settings' },
    )
    .navigatePart('admin-menu', ['admin-menu']);

const isAdmin = () =>
  firstValueFrom(inject(PermissionsService).currentUser$.pipe(map((user) => user.isAdmin)));

export const workbenchConfig = provideWorkbench({
  // Delegates SCION Workbench's `%key` translation syntax to ngx-translate, so part labels use
  // the same translation keys and service as the rest of the application.
  textProvider: (key, params) => translate(key, params),
  // Resolves custom application icons, in addition to Material ligatures.
  iconProvider: appIconProvider,
  // one perspective per role with opposite checks, so only one gets registered. scion stores the
  // layout per perspective and never restores an unregistered one, an admin layout can't leak to a
  // non-admin on the same browser. `default` is the id scion uses for a plain layout, keeps the
  // layouts stored before MON-199 working
  layout: {
    perspectives: [
      { id: 'default', layout: baseLayout, canActivate: async () => !(await isAdmin()) },
      { id: 'admin', layout: adminLayout, canActivate: isAdmin },
    ],
  },
});
