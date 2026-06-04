import {
  MAIN_AREA,
  provideWorkbench,
  WorkbenchLayoutFactory,
} from '@scion/workbench';

export const workbenchConfig = provideWorkbench({
  layout: (factory: WorkbenchLayoutFactory) =>
    factory
      .addPart(MAIN_AREA)
      .navigatePart(MAIN_AREA, ['overview'])
      .addPart(
        'metrics-menu',
        { dockTo: 'left-top' },
        { label: 'Demo', icon: 'data_exploration'},
      )
      .navigatePart('metrics-menu', ['metrics-menu'])
      .activatePart('metrics-menu'),
});
