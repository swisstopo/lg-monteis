import { JsonPipe } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, FormGroupDirective, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatAutocomplete, MatAutocompleteTrigger, MatOption } from '@angular/material/autocomplete';
import { MatButton } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatFormField, MatInput, MatLabel } from '@angular/material/input';
import { WorkbenchView } from '@scion/workbench';
import { startWith } from 'rxjs';

import {
  CreateFormulaDto,
  CreateSensorDto,
  ErrorDTO,
  FormulaResponseDto,
  SensorControllerService,
  SensorResponseDto,
} from '../../../core/generated';

import { FormErrorService } from '../../../core/errors/form-error.service';
import { Notification } from '../../../core/services/notification';
import { FormErrorComponent } from '../../../core/shared/components/form-error.component/form-error.component';

@Component({
  selector: 'app-sensor-create',
  standalone: true,
  imports: [
    MatFormField,
    MatFormFieldModule,
    ReactiveFormsModule,
    MatLabel,
    MatInput,
    MatButton,
    JsonPipe,
    MatAutocomplete,
    MatAutocompleteTrigger,
    MatOption,
    FormErrorComponent,
  ],
  templateUrl: './sensor-create.html',
  styleUrl: './sensor-create.scss',
})
export default class SensorCreate {
  constructor(view: WorkbenchView) {
    effect(() => {
      view.title = 'Create New Sensor';
    });
  }

  private fb = inject(FormBuilder);
  private sensorService = inject(SensorControllerService);
  private formErrorService = inject(FormErrorService);
  private notification = inject(Notification);

  protected serverResponse = signal<SensorResponseDto | null>(null);
  protected serverError = signal<ErrorDTO[] | null>(null); // Typed as array because interceptor normalizes it
  protected status = signal<number | null>(null);

  private formulasResource = rxResource({
    stream: () => this.sensorService.findAllFormulas(),
  });

  protected allFormulas = computed(() => this.formulasResource.value() ?? []);

  protected sensorForm = this.fb.group({
    code: ['', [Validators.required]],
    name: ['', [Validators.required]],
    lowerBound: [0, [Validators.required]],
    upperBound: [100, [Validators.required]],
    formulaControl: ['' as string | FormulaResponseDto | null],
  });

  private rawSearchText = toSignal(
    this.sensorForm.controls.formulaControl.valueChanges.pipe(startWith('')),
    { initialValue: '' },
  );

  protected filteredFormulas = computed(() => {
    const search = this.rawSearchText();
    const list = this.allFormulas();

    if (typeof search !== 'string') return list;

    return list.filter((formula) =>
      (formula.expression ?? '').toLowerCase().includes(search.toLowerCase()),
    );
  });

  protected displayFormulaFn(formula: FormulaResponseDto | null): string {
    return formula?.expression ?? '';
  }

  protected onSubmit(formDirective: FormGroupDirective): void {
    if (this.sensorForm.invalid) {
      return;
    }

    this.serverResponse.set(null);
    this.serverError.set(null);
    this.status.set(null);
    this.clearBackendErrors();

    const payload = this.mapFormToPayload(this.sensorForm.value);

    this.sensorService.createSensor(payload, 'response').subscribe({
      next: (response) => {
        // Populate success debug signals
        this.status.set(response.status);
        this.serverResponse.set(response.body);

        this.formulasResource.reload();
        this.notification.success();

        formDirective.resetForm({
          ...this.sensorForm.value,
          formulaControl: '',
        });
      },
      error: (errors: ErrorDTO[]) => {
        // Populate error debug signals
        this.status.set(422); // Fallback assumption since the interceptor throws the raw array
        this.serverError.set(errors);

        if (errors && errors.length > 0) {
          this.formErrorService.applyErrors(this.sensorForm, errors);
        }
      },
    });
  }

  private clearBackendErrors(): void {
    Object.values(this.sensorForm.controls).forEach((control) => {
      const errors = control.errors;
      if (!errors?.['backend']) return;

      const { backend, ...remaining } = errors;
      control.setErrors(Object.keys(remaining).length ? remaining : null);
    });
  }

  private mapFormToPayload(formValue: typeof this.sensorForm.value): CreateSensorDto {
    const rawFormulaValue = formValue.formulaControl;

    const formulaPayload: CreateFormulaDto | undefined =
      typeof rawFormulaValue === 'object' && rawFormulaValue
        ? { ...rawFormulaValue, expression: rawFormulaValue.expression ?? '' }
        : typeof rawFormulaValue === 'string' && rawFormulaValue.trim()
          ? { expression: rawFormulaValue.trim() }
          : undefined;

    return {
      code: formValue.code ?? '',
      name: formValue.name ?? '',
      lowerBound: Number(formValue.lowerBound),
      upperBound: Number(formValue.upperBound),
      formula: formulaPayload,
    };
  }
}
