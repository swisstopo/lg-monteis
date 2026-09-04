import { WorkbenchIconDescriptor } from '@scion/workbench';
import { MaterialIcon } from '../../ui/icons/material-icon';
import { MeasurementsIcon } from '../../ui/icons/measurements-icon';
import { appIconProvider } from './icon-provider';

describe('appIconProvider', () => {
  it('renders a Material icon', () => {
    const descriptor = appIconProvider('settings') as WorkbenchIconDescriptor;

    expect(descriptor.component).toBe(MaterialIcon);
    expect(descriptor.inputs).toEqual({ ligature: 'settings' });
  });

  it('resolves a custom application icon', () => {
    expect(appIconProvider('app.measurements')).toBe(MeasurementsIcon);
  });

  it('leaves icons of the workbench itself to the workbench', () => {
    expect(appIconProvider('workbench.close')).toBeUndefined();
  });
});
