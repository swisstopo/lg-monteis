import { Component, computed, effect, inject, input, linkedSignal, signal } from '@angular/core';
import {
  form,
  FormField,
  maxLength,
  minLength,
  required,
  submit,
  validate,
} from '@angular/forms/signals';
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
  FormulaResponseDto,
  SensorResponseDto,
  SensorTypeResponseDto,
  WriteSensorDto,
} from '../../../core/generated';
import { toErrorDtos } from '../../../core/http/api-error.model';
import { ToastService } from '../../../core/notifications/toast.service';
import { FormErrorService } from '../../../core/utils/form-error.service';
import { getUnitMetadata, Unit } from '../models/sensor.model';
import { SensorService } from '../services/sensor.service';

interface SensorFormData {
  code: string;
  name: string;
  unit: Unit;
  type: {
    name: string;
  };
  comment: string;
  coordinates: {
    x: number;
    y: number;
    z: number;
  };
  alarmLimits: {
    lower: number;
    upper: number;
  };
  active: boolean;
  formula: {
    expression: string;
  };
}

function domainModelToFormModel(domainModel: SensorResponseDto): SensorFormData {
  return {
    active: domainModel.active ?? true,
    code: domainModel.code ?? '',
    name: domainModel.name ?? '',
    comment: domainModel.comment ?? '',
    formula: {
      expression: domainModel.formula?.expression ?? '',
    },
    alarmLimits: {
      lower: domainModel.alarmLimits?.lower ?? 0,
      upper: domainModel.alarmLimits?.upper ?? 100,
    },
    type: {
      name: domainModel.type?.name ?? '',
    },
    unit: domainModel.unit ?? WriteSensorDto.UnitEnum.Ampere,
    coordinates: {
      x: domainModel.coordinates?.x ?? 0,
      y: domainModel.coordinates?.y ?? 0,
      z: domainModel.coordinates?.z ?? 0,
    },
  };
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
  private readonly translateService = inject(TranslateService);
  private readonly formErrorService = inject(FormErrorService);
  readonly dialogRef = inject<MatDialogRef<SensorEdit>>(MatDialogRef, {
    optional: true,
  });
  readonly sensorId = input<number | undefined>(undefined);

  readonly unitValues = Object.values(WriteSensorDto.UnitEnum) as Unit[];
  readonly unitMetadata = getUnitMetadata();
  readonly allFormulas = this.sensorService.allFormulas;
  readonly allTypes = this.sensorService.allTypes;
  selectedFormula = signal<FormulaResponseDto | null>(null);
  selectedType = signal<SensorTypeResponseDto | null>(null);

  readonly saveError = this.sensorService.error;
  sensor = signal<SensorResponseDto | undefined>(undefined);
  title = computed(() =>
    this.sensorId()
      ? this.translateService.translate('sensor.edit.title.edit')()
      : this.translateService.translate('sensor.edit.title.create')(),
  );

  private readonly syncSelectedSensor = effect(() => {
    this.sensorService.getSensor(this.sensorId());
  });

  private readonly applyLoadedSensor = effect(() => {
    try {
      this.sensor.set(this.sensorService.sensor.value());
      if (this.sensor() !== undefined) {
        this.domainModel.set(this.sensor()!);
        this.sensorForm().markAsTouched();
      }
    } catch {
      const errors = toErrorDtos(this.sensorService.sensor.error());
      errors.forEach((err) =>
        this.toastService.error(
          translate(err?.messageKey ?? 'sensor.error.unspecified.message')(),
          translate('sensor.error.unspecified.title')(),
        ),
      );
    }
  });

  readonly domainModel = signal<SensorResponseDto>({});
  private readonly formModel = linkedSignal({
    source: this.domainModel,
    computation: (domainModel) =>
      domainModel ? domainModelToFormModel(domainModel) : this.initSensorModel(),
  });

  private initSensorModel(): SensorFormData {
    return {
      code: '',
      name: '',
      unit: WriteSensorDto.UnitEnum.Ampere,
      type: {
        name: '',
      },
      comment: '',
      coordinates: {
        x: 0,
        y: 0,
        z: 0,
      },
      alarmLimits: {
        lower: 0,
        upper: 100,
      },
      active: true,
      formula: {
        expression: '',
      },
    };
  }

  readonly sensorForm = form(this.formModel, (schema) => {
    required(schema.code, { message: translate('sensor.code.validation.required')() });
    required(schema.name, { message: translate('sensor.name.validation.required')() });
    minLength(schema.name, 2, { message: translate('sensor.name.validation.minLength')() });
    maxLength(schema.name, 50, { message: translate('sensor.name.validation.maxLength')() });
    required(schema.unit);
    required(schema.type.name, {
      message: translate('sensor.type.validation.required')(),
    });
    required(schema.coordinates.x, {
      message: translate('sensor.coordinate.xLocal.validation.required')(),
    });
    required(schema.coordinates.y, {
      message: translate('sensor.coordinate.yLocal.validation.required')(),
    });
    required(schema.coordinates.z, {
      message: translate('sensor.coordinate.zLocal.validation.required')(),
    });
    required(schema.alarmLimits.lower, {
      message: translate('sensor.alarmLimit.from.validation.required')(),
    });
    required(schema.alarmLimits.upper, {
      message: translate('sensor.alarmLimit.to.validation.required')(),
    });
    validate(schema.alarmLimits.lower, ({ value, valueOf }) => {
      const lower = value();
      const upper = valueOf(schema.alarmLimits.upper);
      if (lower > upper) {
        return {
          kind: 'bounds',
          message: this.translateService.translate('sensor.alarmLimit.from.validation.bounds')(),
        };
      }
      return undefined;
    });
    validate(schema.alarmLimits.upper, ({ value, valueOf }) => {
      const upper = value();
      const lower = valueOf(schema.alarmLimits.lower);
      if (upper < lower) {
        return {
          kind: 'bounds',
          message: this.translateService.translate('sensor.alarmLimit.to.validation.bounds')(),
        };
      }
      return undefined;
    });
  });

  readonly filteredFormulas = computed(() => {
    const search = this.sensorForm.formula().value();
    const list = this.allFormulas.value() ?? [];

    if (!search) return list;

    return list.filter((formula) =>
      (formula.expression ?? '').toLowerCase().includes(search.expression.toLowerCase()),
    );
  });

  selectFormula(formula: FormulaResponseDto): void {
    this.sensorForm.formula.expression().value.set(formula.expression ?? '');
    this.selectedFormula.set(formula);
  }

  readonly filteredTypes = computed(() => {
    const search = this.sensorForm.type().value();
    const list = this.allTypes.value() ?? [];

    if (!search) return list;

    return list.filter((type) =>
      (type.name ?? '').toLowerCase().includes(search.name.toLowerCase()),
    );
  });

  selectType(type: SensorTypeResponseDto): void {
    this.sensorForm.type.name().value.set(type.name ?? '');
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
      const sensor = this.buildPayload(this.formModel());

      try {
        await this.saveSensor(sensor);

        this.allFormulas.reload();
        this.allTypes.reload();

        this.toastService.success(this.translateService.translate('sensor.success')());

        if (resetAfter) {
          this.resetForm();
        } else {
          this.dialogRef?.close();
        }
        return;
      } catch {
        return this.formErrorService.mapApiErrorsToFormErrors(
          this.saveError(),
          this.sensorForm,
          'sensor.error.unspecified.message',
        );
      }
    });
  }

  private resetForm(): void {
    this.domainModel.set({});
    this.sensorService.getSensor(undefined);
    this.sensorForm().reset();
  }

  private buildPayload(formData: SensorFormData): WriteSensorDto {
    return {
      id: this.sensor()?.id ?? undefined,
      code: formData.code,
      name: formData.name,
      unit: formData.unit,
      type: { name: formData.type.name },
      comment: formData.comment ?? undefined,
      coordinates: {
        x: Number(formData.coordinates.x),
        y: Number(formData.coordinates.y),
        z: Number(formData.coordinates.z),
      },
      alarmLimits: {
        lower: Number(formData.alarmLimits.lower),
        upper: Number(formData.alarmLimits.upper),
      },
      active: Boolean(formData.active),
      formula: formData.formula.expression
        ? { expression: formData.formula.expression }
        : undefined,
      version: this.sensor()?.version ?? undefined,
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
