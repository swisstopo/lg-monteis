import { Component, input } from '@angular/core';
import { MatIcon } from '@angular/material/icon';

@Component({
  imports: [MatIcon],
  selector: 'app-inline-error',
  styleUrl: './inline-error.scss',
  templateUrl: './inline-error.html',
})
export class InlineError {
  readonly message = input.required<string>();
}
