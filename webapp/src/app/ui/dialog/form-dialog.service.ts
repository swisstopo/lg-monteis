import { inject, Injectable, inputBinding, InputSignalWithTransform, Type } from '@angular/core';
import { MatDialog, MatDialogConfig, MatDialogRef } from '@angular/material/dialog';

/** The inputs of component `C` by name, each with the value type it accepts. */
export type DialogInputs<C> = {
  [
    K in keyof C as C[K] extends InputSignalWithTransform<any, any> ? K : never
  ]?: C[K] extends InputSignalWithTransform<any, infer Value> ? Value : never;
};

// one size and focus for every create and edit dialog, so they all open the same
const FORM_DIALOG_CONFIG: MatDialogConfig = {
  width: '60vw',
  maxWidth: '1200px',
  autoFocus: true,
};

@Injectable({ providedIn: 'root' })
export class FormDialogService {
  private readonly dialog = inject(MatDialog);

  /**
   * Opens `component` as a form dialog with `inputs`. A wrong input name or value type does not
   * compile, inputBinding alone takes any string. A missing required input still only shows at
   * runtime: input() and input.required() have the same type.
   */
  open<C>(component: Type<C>, inputs: DialogInputs<C> = {}): MatDialogRef<C> {
    const bindings = Object.entries(inputs).map(([name, value]) => inputBinding(name, () => value));
    return this.dialog.open(component, { ...FORM_DIALOG_CONFIG, bindings });
  }
}
