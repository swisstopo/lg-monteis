import { Component, inject, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { WorkbenchComponent } from '@scion/workbench';
import { OAuthService } from 'angular-oauth2-oidc';
import translationsEN from '../../public/i18n/en.json';
import { ToastComponent } from './ui/toast/toast.component';

@Component({
  selector: 'app-root',
  imports: [WorkbenchComponent, MatButton, ToastComponent, TranslatePipe],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private oauthService = inject(OAuthService);
  private translate = inject(TranslateService);
  protected title = signal('MONTEIS');

  constructor() {
    this.translate.setTranslation('en', translationsEN);
    this.translate.setFallbackLang('en');
  }

  logout(): void {
    this.oauthService.logOut();
  }
}
