import {Component, computed, CUSTOM_ELEMENTS_SCHEMA, input} from '@angular/core';
import Add16 from '@carbon/icons/es/add/16';
import ChevronRight16 from '@carbon/icons/es/chevron--right/16';
import Search16 from '@carbon/icons/es/search/16';
import Bm16 from '@carbon/icons/es/business-metrics/16';
import '@carbon/web-components/es/components/icon/index.js';

@Component({
  selector: 'app-icon',
  imports: [],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './icon.html',
})

export class Icon {
public readonly icon = input.required<string>();
public readonly size = input<string>('16');

protected resolvedIcon = computed(() => {
  switch (this.icon()) {
    case 'add':
      return Add16;
    case 'chevron-right':
      return ChevronRight16;
    case 'business-metrics':
      return Bm16;
    case 'checklist':
      return Search16;
    default:
      console.warn(`[Icon Component] Missing mapping for icon: ${this.icon()}`);
      return Search16;
  }
});
}
