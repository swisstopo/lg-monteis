import { Component, effect, inject, inputBinding } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { MatError, MatFormField, MatInput, MatLabel } from '@angular/material/input';
import { MatRadioButton, MatRadioGroup } from '@angular/material/radio';
import { MatOption, MatSelect } from '@angular/material/select';
import { WorkbenchView } from '@scion/workbench';
import SensorEdit from '../../sensor/sensor-edit/sensor-edit';

@Component({
  selector: 'app-dialog',
  imports: [
    ReactiveFormsModule,
    MatCheckbox,
    MatFormField,
    MatLabel,
    MatIcon,
    MatRadioGroup,
    MatRadioButton,
    MatInput,
    MatSelect,
    MatOption,
    MatError,
    MatButton,
  ],
  templateUrl: './dialog.html',
  styleUrl: './dialog.scss',
})
export default class Dialog {
  private fb = inject(FormBuilder);
  private dialog = inject(MatDialog);

  protected form = this.fb.nonNullable.group({
    checkbox1: [true],
    checkbox2: [false],
    checkbox3: [{ value: false, disabled: true }],

    numberInput: [50],

    files: [[] as File[]],

    radioSelection: ['default-selected'],

    search: [''],
    selectOption: [''],
    textInput: [''],

    password: [
      '',
      [Validators.required, Validators.pattern('^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z]).{6,}$')],
    ],

    textArea: [''],
  });

  onFileSelected(event: Event) {
    const element = event.currentTarget as HTMLInputElement;
    const fileList: FileList | null = element.files;
    if (fileList) {
      this.form.controls.files.setValue(Array.from(fileList));
    }
  }

  onCreateDialogOpen() {
    this.dialog.open(SensorEdit, {
      width: '60vw',
      maxWidth: '1200px',
      autoFocus: true,
    });
  }

  onDialogOpen() {
    this.dialog.open(SensorEdit, {
      width: '60vw',
      maxWidth: '1200px',
      autoFocus: true,
      bindings: [inputBinding('sensorId', () => 1)],
    });
  }

  onSubmit() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      console.warn('Form is invalid. Cannot submit.');
      return;
    }

    const payload = this.form.getRawValue();
    console.log('Successfully Submitted Payload:', payload);
  }

  constructor(view: WorkbenchView) {
    // SCION Workbench: Dynamically update the tab title whenever the data changes
    effect(() => {
      view.title = `Form preview`;
    });
  }
}
