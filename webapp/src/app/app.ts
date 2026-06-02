import { Component, signal } from '@angular/core';
import {WorkbenchComponent} from '@scion/workbench';

@Component({
  selector: 'app-root',
  imports: [WorkbenchComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  protected readonly title = signal('MONTEIS');
}
