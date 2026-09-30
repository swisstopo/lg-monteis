import { Component, inject } from '@angular/core';
import { PermissionsService } from '@core/auth/permissions.service';
import { TranslatePipe } from '@ngx-translate/core';
import { RouteButton } from '@ui/buttons/route-button/route-button';

@Component({
  selector: 'app-sensor-menu',
  imports: [RouteButton, TranslatePipe],
  templateUrl: './setup-menu.html',
  styleUrl: './setup-menu.scss',
})
export default class SetupMenu {
  /** The Sensor entry is admin-only; its route is additionally guarded by adminGuard. */
  protected readonly permissions = inject(PermissionsService);
}
