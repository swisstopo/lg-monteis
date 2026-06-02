import {
  Component,
  computed,
  CUSTOM_ELEMENTS_SCHEMA,
  input,
} from '@angular/core';
import { IconName, ICONS } from './icon.registry';
import '@carbon/web-components/es/components/icon/index.js';

@Component({
  selector: 'app-icon',
  imports: [],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './icon.html',
})
export class Icon {
  public readonly icon = input.required<IconName>();
  public readonly size = input<string>('16');

  protected resolvedIcon = computed(() => {
    return ICONS[this.icon()];
  });
}
