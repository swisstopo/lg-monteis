import { translate } from '@ngx-translate/core';
import { MAIN_AREA, provideWorkbench, WorkbenchLayoutFactory } from '@scion/workbench';
import { appIconProvider } from '../core/workbench/icon-provider';

export const workbenchConfig = provideWorkbench({
  // Delegates SCION Workbench's `%key` translation syntax to ngx-translate, so part labels use
  // the same translation keys and service as the rest of the application.
  textProvider: (key, params) => translate(key, params),
  // Resolves custom application icons, in addition to Material ligatures.
  iconProvider: appIconProvider,
  layout: (factory: WorkbenchLayoutFactory) =>
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
      .activatePart('setup-menu'),
});
