import { Component, signal, viewChild} from '@angular/core';
import { MatSidenav, MatSidenavContainer, MatSidenavContent } from '@angular/material/sidenav';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatIcon } from '@angular/material/icon';
import MetricsMenu from '../../features/demo/metrics-menu/metrics-menu';

@Component({
  selector: 'app-app-shell',
  imports: [
    MatSidenavContainer,
    MatSidenav,
    MatSidenavContent,
    RouterOutlet,
    MatIcon,
    RouterLinkActive,
    RouterLink,
    MetricsMenu,
  ],
  templateUrl: './side-nav.html',
  styleUrl: './side-nav.scss',
})
export class SideNav {
  drawer = viewChild.required<MatSidenav>('drawer');

  activeMenu = signal<'demo' | 'settings' | null>(null);

  async toggleMenu(menuName: 'demo' | 'settings') {
    const drawerInstance = this.drawer();

    if (this.activeMenu() === menuName && drawerInstance.opened) {
      await drawerInstance.close();
    } else {
      this.activeMenu.set(menuName);
      await drawerInstance.open();
    }
  }

  async goHome() {
    this.activeMenu.set(null);
    await this.drawer().close();
  }
}
