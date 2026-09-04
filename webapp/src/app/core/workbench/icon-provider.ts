import { ComponentType } from '@angular/cdk/portal';
import { WorkbenchIconProviderFn } from '@scion/workbench';
import { MaterialIcon } from '../../ui/icons/material-icon';
import { MeasurementsIcon } from '../../ui/icons/measurements-icon';

/**
 * Custom application icons, keyed with an `app.` prefix to distinguish them from Material ligatures.
 */
const APP_ICONS: Record<string, ComponentType<unknown>> = {
  'app.measurements': MeasurementsIcon,
};

/**
 * Resolves the icons referenced in the workbench layout, including the custom
 * APP_ICONS.
 */
export const appIconProvider: WorkbenchIconProviderFn = (icon) => {
  if (icon.startsWith('workbench.')) {
    return undefined;
  }
  if (icon in APP_ICONS) {
    return APP_ICONS[icon];
  }
  return { component: MaterialIcon, inputs: { ligature: icon } };
};
