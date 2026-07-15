import { JsonPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
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
  ErrorDto,
  FormulaResponseDto,
  SensorControllerService,
  SensorResponseDto,
  WriteFormulaDto,
  WriteSensorDto,
} from '../../../core/generated';

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

  // --- STRICTLY TYPED JSON DUMP SIGNALS ---
  protected serverResponse = signal<SensorResponseDto | null>(null);
  protected serverError = signal<ErrorDto | null>(null);
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

    // Clear UI dump state before new request
    this.serverResponse.set(null);
    this.serverError.set(null);
    this.status.set(null);

    const payload = this.mapFormToPayload(this.sensorForm.value);

    this.sensorService.createSensor(payload, 'response').subscribe({
      next: (response) => {
        this.status.set(response.status);
        this.serverResponse.set(response.body); // Typed as SensorResponseDto

        this.formulasResource.reload();
        formDirective.resetForm({
          ...this.sensorForm.value,
          formulaControl: '',
        });
      },
      error: (err: HttpErrorResponse) => {
        this.status.set(err.status);

        // Safely typed extraction since your backend advice guarantees this shape!
        const backendError = err.error as ErrorDto;
        this.serverError.set(backendError);
      },
    });
  }

  private mapFormToPayload(formValue: typeof this.sensorForm.value): WriteSensorDto {
    const rawFormulaValue = formValue.formulaControl;

    const formulaPayload: WriteFormulaDto | undefined =
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
