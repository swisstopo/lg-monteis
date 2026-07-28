import { Component, inject, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { WorkbenchComponent } from '@scion/workbench';
import { OAuthService } from 'angular-oauth2-oidc';
import { ToastComponent } from './ui/toast/toast.component';

@Component({
  selector: 'app-root',
  imports: [WorkbenchComponent, MatButton, ToastComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly title = signal('MONTEIS');

  private readonly oauthService = inject(OAuthService);

  constructor() {}

  protected logout(): void {
    this.oauthService.logOut();
  }
}
