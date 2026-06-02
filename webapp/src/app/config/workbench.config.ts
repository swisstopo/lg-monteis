import {
  MAIN_AREA,
  provideWorkbench,
  WorkbenchIconDescriptor,
  WorkbenchLayoutFactory,
} from '@scion/workbench';
import { Icon } from '../shared/icon/icon';

export const workbenchConfig = provideWorkbench({
  iconProvider: (icon: string): WorkbenchIconDescriptor | undefined => {
    if (icon.startsWith('workbench.')) {
      return undefined; // return `undefined` to not replace built-in workbench icons
    }
    return {
      component: Icon,
      inputs: { icon, size: '16' },
    };
  },
  layout: (factory: WorkbenchLayoutFactory) =>
    factory
      .addPart(MAIN_AREA)
      .navigatePart(MAIN_AREA, ['overview'])
      .addPart(
        'metrics-menu',
        { dockTo: 'left-top' },
        { label: 'Time Series', icon: 'business-metrics' },
      )
      .navigatePart('metrics-menu', ['metrics-menu'])
      .activatePart('metrics-menu'),
});
