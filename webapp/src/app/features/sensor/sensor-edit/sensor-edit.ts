import { Component, computed, effect, inject, input, linkedSignal, signal } from '@angular/core';
import {
  applyEach,
  FieldTree,
  form,
  FormField,
  maxLength,
  minLength,
  required,
  submit,
  validate,
} from '@angular/forms/signals';
import { MatAutocomplete, MatAutocompleteTrigger } from '@angular/material/autocomplete';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatOption } from '@angular/material/core';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatError, MatFormField, MatInput, MatLabel } from '@angular/material/input';
import { MatSelect } from '@angular/material/select';
import {
  ExperimentResponseDto,
  FormulaResponseDto,
  SensorResponseDto,
  SensorTypeResponseDto,
  WriteSensorDto,
  WriteSensorParameterDto,
} from '@core/generated';
import { toErrorDtos } from '@core/http/api-error.model';
import { ToastService } from '@core/notifications/toast.service';
import { FormErrorService } from '@core/utils/form-error.service';
import { ExperimentService } from '@features/experiment/services/experiment.service';
import { Das, getDasMetadata, getUnitMetadata, Unit } from '@features/sensor/models/sensor.model';
import { SensorService } from '@features/sensor/services/sensor.service';
import { translate, TranslatePipe, TranslateService } from '@ngx-translate/core';

// TODO: MON-145 refactor sensor parameter into own component
interface SensorParameterFormData {
  // Component-local synthetic key, never sent to the backend. Needed as a stable @for track
  // identity because a parameter's real id is undefined until first saved, and array index is
  // unsafe to track by once removal-from-the-middle is possible.
  clientKey: string;
  // Backend id: populated from the loaded sensor for existing parameters, left undefined for
  // parameters added client-side via "+ Add Parameter". Carried through unchanged into the
  // submitted payload so updateSensor can tell which array items are existing vs new.
  id: string | undefined;
  name: string;
  dasParameterAlias: string;
  unit: Unit;
  type: {
    name: string;
  };
  formula: {
    expression: string;
  };
  alarmLimits: {
    lower: number;
    upper: number;
  };
  active: boolean;
  comment: string;
}

interface SensorFormData {
  name: string;
  dasSensorAlias: string;
  das: Das;
  fulcrumId: string;
  comment: string;
  active: boolean;
  coordinates: {
    x: number;
    y: number;
    z: number;
  };
  mainExperiment: {
    name: string;
  };
  parameters: SensorParameterFormData[];
}

function blankParameter(): SensorParameterFormData {
  return {
    clientKey: crypto.randomUUID(),
    id: undefined,
    name: '',
    dasParameterAlias: '',
    unit: WriteSensorParameterDto.UnitEnum.Ampere,
    type: { name: '' },
    formula: { expression: '' },
    alarmLimits: { lower: 0, upper: 100 },
    active: true,
    comment: '',
  };
}

function domainModelToFormModel(domainModel: SensorResponseDto): SensorFormData {
  const parameters: SensorParameterFormData[] =
    domainModel.parameters && domainModel.parameters.length > 0
      ? domainModel.parameters.map((parameter) => ({
          clientKey: crypto.randomUUID(),
          id: parameter.id,
          name: parameter.name ?? '',
          dasParameterAlias: parameter.dasParameterAlias ?? '',
          unit: parameter.unit ?? WriteSensorParameterDto.UnitEnum.Ampere,
          type: { name: parameter.type?.name ?? '' },
          formula: { expression: parameter.formula?.expression ?? '' },
          alarmLimits: {
            lower: parameter.alarmLimits?.lower ?? 0,
            upper: parameter.alarmLimits?.upper ?? 100,
          },
          active: parameter.active ?? true,
          comment: parameter.comment ?? '',
        }))
      : [blankParameter()];

  return {
    active: domainModel.active ?? true,
    dasSensorAlias: domainModel.dasSensorAlias ?? '',
    name: domainModel.name ?? '',
    comment: domainModel.comment ?? '',
    das: domainModel.das ?? WriteSensorDto.DasEnum.SolExperts,
    fulcrumId: domainModel.fulcrumId ?? '',
    coordinates: {
      x: domainModel.coordinates?.x ?? 0,
      y: domainModel.coordinates?.y ?? 0,
      z: domainModel.coordinates?.z ?? 0,
    },
    mainExperiment: {
      name: domainModel.mainExperiment?.name ?? '',
    },
    parameters,
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
    MatIconButton,
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
  private readonly experimentService = inject(ExperimentService);
  private readonly toastService = inject(ToastService);
  private readonly translateService = inject(TranslateService);
  private readonly formErrorService = inject(FormErrorService);
  readonly dialogRef = inject<MatDialogRef<SensorEdit>>(MatDialogRef, {
    optional: true,
  });
  readonly sensorId = input<string | undefined>(undefined);

  readonly unitValues = Object.values(WriteSensorParameterDto.UnitEnum) as Unit[];
  readonly unitMetadata = getUnitMetadata();
  readonly dasValues = Object.values(WriteSensorDto.DasEnum) as Das[];
  readonly dasMetadata = getDasMetadata();
  readonly allFormulas = this.sensorService.allFormulas;
  readonly allTypes = this.sensorService.allTypes;
  readonly allExperiments = this.experimentService.allExperiments;
  selectedExperiment = signal<ExperimentResponseDto | null>(null);

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
        this.selectedExperiment.set(this.sensor()?.mainExperiment ?? null);
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
      dasSensorAlias: '',
      name: '',
      das: WriteSensorDto.DasEnum.SolExperts,
      fulcrumId: '',
      comment: '',
      active: true,
      coordinates: {
        x: 0,
        y: 0,
        z: 0,
      },
      mainExperiment: { name: '' },
      parameters: [blankParameter()],
    };
  }

  readonly sensorForm = form(this.formModel, (schema) => {
    required(schema.dasSensorAlias, {
      message: translate('sensor.dasSensorAlias.validation.required')(),
    });
    required(schema.name, { message: translate('sensor.name.validation.required')() });
    minLength(schema.name, 2, { message: translate('sensor.name.validation.minLength')() });
    maxLength(schema.name, 50, { message: translate('sensor.name.validation.maxLength')() });
    required(schema.das, { message: translate('sensor.das.validation.required')() });
    required(schema.coordinates.x, {
      message: translate('sensor.coordinate.xLocal.validation.required')(),
    });
    required(schema.coordinates.y, {
      message: translate('sensor.coordinate.yLocal.validation.required')(),
    });
    required(schema.coordinates.z, {
      message: translate('sensor.coordinate.zLocal.validation.required')(),
    });
    validate(schema.mainExperiment.name, ({ value }) => {
      const name = value();
      if (!name) return undefined;
      if (this.selectedExperiment()?.name !== name) {
        return {
          kind: 'notSelected',
          message: this.translateService.translate(
            'sensor.mainExperiment.validation.notSelected',
          )(),
        };
      }
      return undefined;
    });

    applyEach(schema.parameters, (parameter) => {
      required(parameter.name, {
        message: translate('sensor.parameter.name.validation.required')(),
      });
      required(parameter.unit);
      required(parameter.type.name, {
        message: translate('sensor.type.validation.required')(),
      });
      required(parameter.alarmLimits.lower, {
        message: translate('sensor.alarmLimit.from.validation.required')(),
      });
      required(parameter.alarmLimits.upper, {
        message: translate('sensor.alarmLimit.to.validation.required')(),
      });
      validate(parameter.alarmLimits.lower, ({ value, valueOf }) => {
        const lower = value();
        const upper = valueOf(parameter.alarmLimits.upper);
        if (lower > upper) {
          return {
            kind: 'bounds',
            message: this.translateService.translate('sensor.alarmLimit.from.validation.bounds')(),
          };
        }
        return undefined;
      });
      validate(parameter.alarmLimits.upper, ({ value, valueOf }) => {
        const upper = value();
        const lower = valueOf(parameter.alarmLimits.lower);
        if (upper < lower) {
          return {
            kind: 'bounds',
            message: this.translateService.translate('sensor.alarmLimit.to.validation.bounds')(),
          };
        }
        return undefined;
      });
    });
  });

  filteredTypesFor(parameter: FieldTree<SensorParameterFormData>): SensorTypeResponseDto[] {
    const search = parameter.type.name().value();
    const list = this.allTypes.value() ?? [];
    if (!search) return list;
    return list.filter((type) => (type.name ?? '').toLowerCase().includes(search.toLowerCase()));
  }

  selectType(parameter: FieldTree<SensorParameterFormData>, type: SensorTypeResponseDto): void {
    parameter.type.name().value.set(type.name ?? '');
  }

  filteredFormulasFor(parameter: FieldTree<SensorParameterFormData>): FormulaResponseDto[] {
    const search = parameter.formula.expression().value();
    const list = this.allFormulas.value() ?? [];
    if (!search) return list;
    return list.filter((formula) =>
      (formula.expression ?? '').toLowerCase().includes(search.toLowerCase()),
    );
  }

  selectFormula(parameter: FieldTree<SensorParameterFormData>, formula: FormulaResponseDto): void {
    parameter.formula.expression().value.set(formula.expression ?? '');
  }

  readonly filteredExperiments = computed(() => {
    const search = this.sensorForm.mainExperiment.name().value();
    const list = this.allExperiments.value() ?? [];
    if (!search) return list;
    return list.filter((experiment) =>
      (experiment.name ?? '').toLowerCase().includes(search.toLowerCase()),
    );
  });

  selectExperiment(experiment: ExperimentResponseDto): void {
    this.sensorForm.mainExperiment.name().value.set(experiment.name ?? '');
    this.selectedExperiment.set(experiment);
  }

  addParameter(): void {
    this.formModel.update((data) => ({
      ...data,
      parameters: [...data.parameters, blankParameter()],
    }));
  }

  removeParameter(index: number): void {
    this.formModel.update((data) => ({
      ...data,
      parameters: data.parameters.filter((_, i) => i !== index),
    }));
  }

  canRemoveParameter(): boolean {
    return this.formModel().parameters.length > 1;
  }

  // The @for template tracks parameter rows by this plain (non-FieldTree) key array rather
  // than reading the clientKey through the form field itself. Angular's @for re-evaluates the
  // track expression against the *live* FieldTree reference of a just-removed row while
  // reconciling the DOM, and a FieldTree for a row no longer present in the array throws
  // (NG01904 "Orphan field") instead of returning a stable value - reading the key from the
  // plain underlying model sidesteps that entirely while still keying by identity (not index),
  // so a row's DOM/focus/touched state still tracks the same logical parameter across reorders.
  parameterKeys(): string[] {
    return this.formModel().parameters.map((parameter) => parameter.clientKey);
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
    this.selectedExperiment.set(null);
    this.sensorService.getSensor(undefined);
    this.sensorForm().reset();
  }

  private buildPayload(formData: SensorFormData): WriteSensorDto {
    // Trim whitespace and convert empty comments to undefined
    const cleanedComment = formData.comment?.trim() || undefined;
    const cleanedFulcrumId = formData.fulcrumId?.trim() || undefined;

    const selected = this.selectedExperiment();
    const mainExperimentId =
      selected && selected.name === formData.mainExperiment.name ? selected.id : undefined;

    return {
      id: this.sensor()?.id ?? undefined,
      name: formData.name,
      dasSensorAlias: formData.dasSensorAlias,
      das: formData.das,
      fulcrumId: cleanedFulcrumId,
      mainExperimentId,
      comment: cleanedComment,
      coordinates: {
        x: Number(formData.coordinates.x),
        y: Number(formData.coordinates.y),
        z: Number(formData.coordinates.z),
      },
      active: Boolean(formData.active),
      version: this.sensor()?.version ?? undefined,
      parameters: formData.parameters.map((parameter) => ({
        id: parameter.id,
        name: parameter.name,
        dasParameterAlias: parameter.dasParameterAlias?.trim() || undefined,
        unit: parameter.unit,
        type: { name: parameter.type.name },
        alarmLimits: {
          lower: Number(parameter.alarmLimits.lower),
          upper: Number(parameter.alarmLimits.upper),
        },
        active: Boolean(parameter.active),
        formula: parameter.formula.expression
          ? { expression: parameter.formula.expression }
          : undefined,
        comment: parameter.comment?.trim() || undefined,
      })),
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
