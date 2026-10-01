import { Component } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

// TODO: placeholder target for the admin menu entry; the organisation table itself is a follow-up story.
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
