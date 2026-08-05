import { translate } from '@ngx-translate/core';
import { MAIN_AREA, provideWorkbench, WorkbenchLayoutFactory } from '@scion/workbench';

export const workbenchConfig = provideWorkbench({
  // Delegates SCION Workbench's `%key` translation syntax to ngx-translate, so part labels use
  // the same translation keys and service as the rest of the application.
  textProvider: (key, params) => translate(key, params),
  layout: (factory: WorkbenchLayoutFactory) =>
    factory
      .addPart(MAIN_AREA)
      .navigatePart(MAIN_AREA, ['overview'])
      .addPart(
        'metrics-menu',
        { dockTo: 'left-top' },
        { label: '%menu.overview', icon: 'data_exploration' },
      )
      .navigatePart('metrics-menu', ['metrics-menu'])
      .activatePart('metrics-menu')
      .addPart('sensor-menu', { dockTo: 'left-top' }, { label: '%menu.setup', icon: 'settings' })
      .navigatePart('sensor-menu', ['sensor-menu'])
      .activatePart('sensor-menu'),
});
