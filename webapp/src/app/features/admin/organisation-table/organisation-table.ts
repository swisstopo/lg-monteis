import { Component } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

// TODO: placeholder so the admin menu entry has a target, the organisation table follows in its own story
@Component({
  selector: 'app-organisation-table',
  imports: [TranslatePipe],
  template: `<p class="placeholder">{{ 'admin.organisation.placeholder' | translate }}</p>`,
  styles: `
    .placeholder {
      margin: 1rem;
    }
  `,
})
export default class OrganisationTable {}
