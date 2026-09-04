import { Component, input } from '@angular/core';

/**
 * Renders a Material Symbols ligature, mirroring the icon component the SCION Workbench uses by
 * default. Needed in the custom `appIconProvider`.
 */
@Component({
  host: { class: 'material-symbols-rounded' },
  selector: 'app-material-icon',
  template: '{{ ligature() }}',
})
export class MaterialIcon {
  readonly ligature = input.required<string>();
}
