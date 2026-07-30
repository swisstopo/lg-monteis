import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { form, FormField, maxLength, minLength, required, submit } from '@angular/forms/signals';
import { MatAutocomplete, MatAutocompleteTrigger } from '@angular/material/autocomplete';
import { MatButton } from '@angular/material/button';
import { MatOption } from '@angular/material/core';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatError, MatFormField, MatInput, MatLabel } from '@angular/material/input';
import { MatSelect } from '@angular/material/select';
import { translate, TranslatePipe, TranslateService } from '@ngx-translate/core';
import {
  ErrorDto,
  FormulaResponseDto,
  SensorResponseDto,
  SensorTypeResponseDto,
  WriteSensorDto,
} from '../../../core/generated';
import { toErrorDtos } from '../../../shared/models/api-error.model';
import { ToastService } from '../../../shared/services/toast.service';
import { Unit, UNIT_METADATA } from '../models/sensor.model';
import { SensorService } from '../services/sensor.service';

interface SensorFormData {
  code: string;
  name: string;
  unit: Unit;
  type: string;
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
  selector: 'app-sensor-edit',
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
    TranslatePipe,
  ],
  templateUrl: './sensor-edit.html',
  styleUrl: './sensor-edit.scss',
})
export default class SensorEdit {
  private readonly sensorService = inject(SensorService);
  private readonly toastService = inject(ToastService);
  private readonly i18nService = inject(TranslateService);
  readonly dialogRef = inject<MatDialogRef<SensorEdit>>(MatDialogRef, {
    optional: true,
  });
  readonly sensorId = input<number | undefined>(undefined);

  readonly unitValues = Object.values(WriteSensorDto.UnitEnum) as Unit[];
  readonly unitMetadata = UNIT_METADATA;
  readonly allFormulas = this.sensorService.allFormulas;
  readonly allTypes = this.sensorService.allTypes;
  selectedFormula = signal<FormulaResponseDto | null>(null);
  selectedType = signal<SensorTypeResponseDto | null>(null);

  readonly saveError = this.sensorService.error;
  sensor = signal<SensorResponseDto | undefined>(undefined);
  title = signal(
    this.sensorId()
      ? translate('sensor.edit.title.edit')()
      : translate('sensor.edit.title.create')(),
  );
  sensorModel = signal<SensorFormData>(this.initSensorModel());

  private readonly syncSelectedSensor = effect(() => {
    this.sensorService.getSensor(this.sensorId());
  });

  private readonly applyLoadedSensor = effect(() => {
    try {
      this.sensor.set(this.sensorService.sensor.value());
      if (this.sensor() !== undefined) {
        this.setSensorModel(this.sensor()!);
        this.sensorForm().markAsTouched();
      }
    } catch {
      const errors = toErrorDtos(this.sensorService.sensor.error());
      errors.forEach((err) =>
        this.toastService.error(
          translate(err?.messageKey ?? 'sensor.edit.error.unspecified.message')(),
          translate('sensor.edit.error.unspecified.title')(),
        ),
      );
    }
  });

  protected close(): void {
    this.dialogRef?.close();
  }

  private initSensorModel() {
    return {
      code: '',
      name: '',
      unit: WriteSensorDto.UnitEnum.Ampere,
      type: '',
      comment: '',
      xLocal: 0,
      yLocal: 0,
      zLocal: 0,
      lowerAlarmBound: 0,
      upperAlarmBound: 100,
      active: true,
      formula: '',
    };
  }

  readonly sensorForm = form(this.sensorModel, (schema) => {
    required(schema.code, { message: translate('sensor.edit.validation.requiredCode')() });
    required(schema.name, { message: translate('sensor.edit.validation.requiredName')() });
    minLength(schema.name, 2, { message: translate('sensor.edit.validation.minLengthName')() });
    maxLength(schema.name, 10, { message: translate('sensor.edit.validation.maxLengthName')() });
    required(schema.unit);
    required(schema.type);
    required(schema.xLocal, { message: translate('sensor.edit.validation.requiredXLocal')() });
    required(schema.yLocal, { message: translate('sensor.edit.validation.requiredYLocal')() });
    required(schema.zLocal, { message: translate('sensor.edit.validation.requiredZLocal')() });
    required(schema.lowerAlarmBound, {
      message: translate('sensor.edit.validation.requiredAlarmLimit')(),
    });
    required(schema.upperAlarmBound, {
      message: translate('sensor.edit.validation.requiredAlarmLimit')(),
    });
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

  readonly filteredTypes = computed(() => {
    const search = this.sensorForm.type().value();
    const list = this.allTypes.value() ?? [];

    if (!search) return list;

    return list.filter((type) => (type.name ?? '').toLowerCase().includes(search.toLowerCase()));
  });

  selectType(type: SensorTypeResponseDto): void {
    this.sensorForm.type().value.set(type.name ?? '');
    this.selectedType.set(type);
  }

  async onSubmit(event: SubmitEvent) {
    event.preventDefault();
    const submitter = event.submitter as HTMLButtonElement | null;
    const resetAfter = submitter?.dataset['action'] === 'saveAndCreate';

    if (this.sensorForm().invalid()) {
      this.sensorForm().markAsTouched();
      return;
    }
    await submit(this.sensorForm, async (field) => {
      const sensor = this.buildPayload(this.sensorModel());

      try {
        await this.saveSensor(sensor);

        this.toastService.success(this.i18nService.instant('sensor.edit.success'));

        if (resetAfter) {
          this.resetForm();
        } else {
          this.dialogRef?.close();
        }
        return;
      } catch {
        const errors = this.saveError();
        if (errors?.[0].target === ErrorDto.TargetEnum.Form) {
          this.toastService.error(
            this.i18nService.instant(
              errors[0].messageKey ?? 'sensor.edit.error.unspecified.message',
            ),
          );
        }

        return errors
          ?.map((err) => {
            const mappedField = err?.field
              ? this.sensorForm[err.field as keyof SensorFormData]
              : undefined;
            if (mappedField) {
              return {
                kind: 'serverError',
                message: err?.messageKey ? this.i18nService.instant(err.messageKey) : '',
                fieldTree: mappedField,
              };
            }
            this.toastService.error(
              this.i18nService.instant(
                errors[0].messageKey ?? 'sensor.edit.error.unspecified.message',
              ),
            );
            return undefined;
          })
          .filter((entry): entry is NonNullable<typeof entry> => entry !== undefined);
      }
    });
  }

  private resetForm(): void {
    this.sensorModel.set(this.initSensorModel());
    this.sensorService.getSensor(undefined);
    this.selectedFormula.set(null);
    this.selectedType.set(null);
    this.sensorForm().reset();
  }

  private setSensorModel(sensor: SensorResponseDto) {
    this.selectedFormula.set(sensor.formula ?? null);
    this.selectedType.set(sensor.type ?? null);

    this.sensorModel.set({
      code: sensor.code ?? '',
      name: sensor.name ?? '',
      unit: (sensor.unit ?? WriteSensorDto.UnitEnum.Ampere) as Unit,
      type: sensor.type?.name ?? '',
      comment: sensor.comment ?? '',
      xLocal: sensor.coordinates?.x ?? 0,
      yLocal: sensor.coordinates?.y ?? 0,
      zLocal: sensor.coordinates?.z ?? 0,
      lowerAlarmBound: sensor.alarmLimits?.lower ?? 0,
      upperAlarmBound: sensor.alarmLimits?.upper ?? 100,
      active: sensor.active ?? true,
      formula: sensor.formula?.expression ?? '',
    });
  }

  private buildPayload(formData: SensorFormData): WriteSensorDto {
    return {
      id: this.sensor()?.id ?? undefined,
      code: formData.code,
      name: formData.name,
      unit: formData.unit,
      type: { name: formData.type },
      comment: formData.comment,
      coordinates: {
        x: Number(formData.xLocal),
        y: Number(formData.yLocal),
        z: Number(formData.zLocal),
      },
      alarmLimits: {
        lower: Number(formData.lowerAlarmBound),
        upper: Number(formData.upperAlarmBound),
      },
      active: Boolean(formData.active),
      formula: { expression: formData.formula },
      // version: this.sensor()?.version ?? undefined,
    };
  }

  private async saveSensor(sensor: WriteSensorDto) {
    if (sensor.id) {
      return await this.sensorService.updateSensor(sensor.id, sensor);
    } else {
      return await this.sensorService.createSensor(sensor);
    }
  }
}
