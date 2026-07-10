import { Component, inject, signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { WorkbenchComponent } from '@scion/workbench';

@Component({
  selector: 'app-root',
  imports: [WorkbenchComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly title = signal('MONTEIS');
  private readonly translate = inject(TranslateService);

  constructor() {
    this.translate.use('en');
  }
}
