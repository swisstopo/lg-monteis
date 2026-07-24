import { MAIN_AREA, provideWorkbench, WorkbenchLayoutFactory } from '@scion/workbench';

export const workbenchConfig = provideWorkbench({
  layout: (factory: WorkbenchLayoutFactory) =>
    factory
      .addPart(MAIN_AREA)
      .navigatePart(MAIN_AREA, ['overview'])
      .addPart(
        'metrics-menu',
        { dockTo: 'left-top' },
        { label: 'Overview', icon: 'data_exploration' },
      )
      .navigatePart('metrics-menu', ['metrics-menu'])
      .activatePart('metrics-menu')
      .addPart('sensor-menu', { dockTo: 'left-top' }, { label: 'Sensor', icon: 'build' })
      .navigatePart('sensor-menu', ['sensor-menu'])
      .activatePart('sensor-menu')
      .addPart(
        'visualization-menu',
        { dockTo: 'left-top' },
        { label: 'Digital Twin', icon: 'view_in_ar' },
      )
      .navigatePart('visualization-menu', ['visualization-menu'])
      .activatePart('visualization-menu'),
});
