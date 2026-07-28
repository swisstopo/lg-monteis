import { Component, computed, inject, input, signal } from '@angular/core';
import { form, FormField, maxLength, min, minLength, required } from '@angular/forms/signals';
import { MatAutocomplete, MatAutocompleteTrigger } from '@angular/material/autocomplete';
import { MatButton } from '@angular/material/button';
import { MatOption } from '@angular/material/core';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatError, MatFormField, MatInput, MatLabel } from '@angular/material/input';
import { MatSelect } from '@angular/material/select';
import {
  ErrorDto,
  FormulaResponseDto,
  SensorResponseDto,
  WriteFormulaDto,
  WriteSensorDto,
} from '../../../core/generated';
import { ToastService } from '../../../shared/services/toast.service';
import { SENSOR_TYPE_METADATA, SensorType, Unit, UNIT_METADATA } from '../models/sensor.model';
import { SensorService } from '../services/sensor.service';

interface SensorFormData {
  code: string;
  name: string;
  unit: Unit;
  type: SensorType;
  comment: string;
  xLocal: number;
  yLocal: number;
  zLocal: number;
  lowerAlarmBound: number;
  upperAlarmBound: number;
  active: boolean;
  formula: string;
}

@Component({
  selector: 'app-sensor-create',
  standalone: true,
  imports: [
    MatError,
    MatFormField,
    MatFormFieldModule,
    MatLabel,
    MatInput,
    MatButton,
    MatSelect,
    MatOption,
    FormField,
    MatDialogModule,
    MatIcon,
    MatAutocomplete,
    MatAutocompleteTrigger,
  ],
  templateUrl: './sensor-create.html',
  styleUrl: './sensor-create.scss',
})
export default class SensorCreate {
  private sensorService = inject(SensorService);
  private toastService = inject(ToastService);
  readonly dialogRef = inject<MatDialogRef<SensorCreate>>(MatDialogRef, {
    optional: true,
  });
  sensorId = input<number | undefined>(undefined);
  // --- STRICTLY TYPED JSON DUMP SIGNALS ---
  serverResponse = signal<SensorResponseDto | null>(null);
  serverError = signal<ErrorDto | null>(null);
  status = signal<number | null>(null);
  readonly unitValues = Object.values(WriteSensorDto.UnitEnum) as Unit[];
  readonly typeValues = Object.values(WriteSensorDto.TypeEnum) as SensorType[];
  protected readonly unitMetadata = UNIT_METADATA;
  protected readonly typeMetadata = SENSOR_TYPE_METADATA;
  readonly allFormulas = this.sensorService.allFormulas;
  selectedFormula = signal<FormulaResponseDto | null>(null);

  saving = this.sensorService.saving;
  saveError = this.sensorService.saveError;
  submitted = signal(false);
  sensorModel = signal<SensorFormData>({
    code: '',
    name: '',
    unit: WriteSensorDto.UnitEnum.Ampere,
    type: WriteSensorDto.TypeEnum.Other,
    comment: '',
    xLocal: 0,
    yLocal: 0,
    zLocal: 0,
    lowerAlarmBound: 0,
    upperAlarmBound: 100,
    active: true,
    formula: '',
  });

  protected close(): void {
    this.dialogRef?.close();
  }

  sensorForm = form(this.sensorModel, (schema) => {
    required(schema.code, { message: 'Sensor code is required' });
    required(schema.name, { message: 'Sensor name is required' });
    minLength(schema.name, 2, { message: 'Sensor name must be at least 2 characters long' });
    maxLength(schema.name, 10, { message: 'Sensor name must be at most 10 characters long' });
    required(schema.unit);
    required(schema.type);
    required(schema.xLocal, { message: 'Sensor xLocal is required' });
    required(schema.yLocal, { message: 'Sensor yLocal is required' });
    required(schema.zLocal, { message: 'Sensor zLocal is required' });
    required(schema.lowerAlarmBound, { message: 'Alarm limit is required' });
    min(schema.lowerAlarmBound, 0, { message: 'Minimal value is 0' });
    required(schema.upperAlarmBound, { message: 'Alarm limit is required' });
  });

  readonly filteredFormulas = computed(() => {
    const search = this.sensorForm.formula().value();
    const list = this.allFormulas.value() ?? [];

    if (!search) return list;

    return list.filter((formula) =>
      (formula.expression ?? '').toLowerCase().includes(search.toLowerCase()),
    );
  });

  selectFormula(formula: FormulaResponseDto): void {
    this.sensorForm.formula().value.set(formula.expression ?? '');
    this.selectedFormula.set(formula);
  }

  async onSubmit(event: Event) {
    event.preventDefault();
    await this.doSave(false);
  }

  protected async saveAndCreate(): Promise<void> {
    await this.doSave(true);
  }

  private async doSave(resetAfter: boolean): Promise<void> {
    this.submitted.set(true);

    if (this.sensorForm().invalid()) {
      return;
    }

    const sensor = this.buildPayload(this.sensorModel());

    try {
      await this.saveSensor(sensor);
      this.toastService.success('Sensor saved successfully.');

      if (resetAfter) {
        this.resetForm();
      } else {
        this.dialogRef?.close();
      }
    } catch {
      const errors = this.saveError();
      errors?.forEach((err) =>
        this.toastService.error(err?.message ?? undefined, err?.title ?? 'Failed to save sensor!'),
      );
    }
  }

  private resetForm(): void {
    this.submitted.set(false);
    this.sensorModel.set({
      code: '',
      name: '',
      unit: WriteSensorDto.UnitEnum.Ampere,
      type: WriteSensorDto.TypeEnum.Other,
      comment: '',
      xLocal: 0,
      yLocal: 0,
      zLocal: 0,
      lowerAlarmBound: 0,
      upperAlarmBound: 100,
      active: true,
      formula: '',
    });
    this.selectedFormula.set(null);
    this.sensorForm().reset();
  }

  private buildPayload(formData: SensorFormData): WriteSensorDto {
    return {
      code: formData.code,
      name: formData.name,
      unit: formData.unit,
      type: formData.type,
      comment: formData.comment ?? '',
      xLocal: Number(formData.xLocal),
      yLocal: Number(formData.yLocal),
      zLocal: Number(formData.zLocal),
      lowerAlarmBound: Number(formData.lowerAlarmBound),
      upperAlarmBound: Number(formData.upperAlarmBound),
      active: Boolean(formData.active),
      formula: this.buildFormulaPayload(formData.formula),
    };
  }

  // Build formula payload from expression
  // If the formula is already known, return the id and version
  // otherwise, send the expression to create a new formula
  private buildFormulaPayload(expression: string): WriteFormulaDto | undefined {
    if (!expression) return undefined;

    const selected = this.selectedFormula();
    if (selected && selected.expression === expression) {
      return { id: selected.id, expression, version: selected.version };
    }
    return { expression };
  }

  private async saveSensor(sensor: WriteSensorDto) {
    if (sensor.id) {
      return await this.sensorService.updateSensor(sensor.id, sensor);
    } else {
      return await this.sensorService.createSensor(sensor);
    }
  }
}
