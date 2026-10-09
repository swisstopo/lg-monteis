import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatDialogActions, MatDialogContent, MatDialogTitle } from '@angular/material/dialog';

/** What a form dialog does with its record: a new one, an existing one, or only showing it. */
export type DialogMode = 'create' | 'edit' | 'view';

/**
 * Title, content and right aligned actions of a dialog. The dismiss action goes in an element with
 * `dialog-action-dismiss`, the submit actions in one with `dialog-action-submit` (an ng-container
 * with ngProjectAs for several). In view mode only the dismiss action shows, there is nothing to
 * submit.
 */
@Component({
  selector: 'app-dialog-layout',
  imports: [MatDialogTitle, MatDialogContent, MatDialogActions],
  template: `
    <h2 mat-dialog-title>{{ title() }}</h2>
    <mat-dialog-content><ng-content /></mat-dialog-content>
    <mat-dialog-actions align="end">
      <ng-content select="[dialog-action-dismiss]" />
      @if (mode() !== 'view') {
        <ng-content select="[dialog-action-submit]" />
      }
    </mat-dialog-actions>
  `,
  // the dialog container lays out title, content and actions as its own children, the host
  // element in between would break that
  styles: ':host { display: contents; }',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DialogLayout {
  readonly title = input.required<string>();
  readonly mode = input.required<DialogMode>();
}
