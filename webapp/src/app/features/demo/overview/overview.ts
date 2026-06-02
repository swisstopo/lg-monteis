import {Component, CUSTOM_ELEMENTS_SCHEMA, inject} from '@angular/core';
import Demo from '../services/demo';
import '@carbon/web-components/es/components/tile/index.js';
import '@carbon/web-components/es/components/loading/index.js';

@Component({
  selector: 'app-overview',
  imports: [],
  templateUrl: './overview.html',
  styleUrl: './overview.scss',
  schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export default class Overview {
  protected demoService = inject(Demo);
}
