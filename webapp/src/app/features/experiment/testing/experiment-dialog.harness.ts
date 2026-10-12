import { ComponentHarness } from '@angular/cdk/testing';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MatDatepickerToggleHarness } from '@angular/material/datepicker/testing';
import { MatFormFieldHarness } from '@angular/material/form-field/testing';
import { MatInputHarness } from '@angular/material/input/testing';
import { ExperimentOwnerListHarness } from './experiment-owner-list.harness';
import { ExperimentOwnerPickerHarness } from './experiment-owner-picker.harness';

const FIELD_LABELS = {
  name: 'experiment.name.label',
  start: 'experiment.period.start.label',
  end: 'experiment.period.end.label',
  comment: 'experiment.comment.label',
} as const;

export type ExperimentField = keyof typeof FIELD_LABELS;

export const EXPERIMENT_FIELDS = Object.keys(FIELD_LABELS) as ExperimentField[];

export class ExperimentDialogHarness extends ComponentHarness {
  static hostSelector = 'app-experiment-dialog';

  private readonly title = this.locatorFor('h2');
  private readonly actions = this.locatorForAll(
    MatButtonHarness.with({ ancestor: 'mat-dialog-actions' }),
  );
  private readonly datepickerToggles = this.locatorForAll(MatDatepickerToggleHarness);
  private readonly ownersOfNewExperiment = this.locatorForOptional(
    MatFormFieldHarness.with({ selector: '[data-testid="owners-create"]' }),
  );

  readonly ownerPicker = this.locatorForOptional(ExperimentOwnerPickerHarness);
  readonly ownerList = this.locatorForOptional(ExperimentOwnerListHarness);

  async getTitle(): Promise<string> {
    return (await this.title()).text();
  }

  async getValue(field: ExperimentField): Promise<string> {
    return (await this.input(field)).getValue();
  }

  async enter(field: ExperimentField, value: string): Promise<void> {
    const input = await this.input(field);
    await input.setValue(value);
    await input.blur();
  }

  async isDisabled(field: ExperimentField): Promise<boolean> {
    return (await this.input(field)).isDisabled();
  }

  async getErrors(field: ExperimentField): Promise<string[]> {
    return (await this.formField(field)).getTextErrors();
  }

  async areDatepickersDisabled(): Promise<boolean> {
    const toggles = await this.datepickerToggles();
    const disabled = await Promise.all(toggles.map((toggle) => toggle.isDisabled()));
    return disabled.length > 0 && disabled.every(Boolean);
  }

  async getActionLabels(): Promise<string[]> {
    return Promise.all((await this.actions()).map((button) => button.getText()));
  }

  async canSave(): Promise<boolean> {
    return !(await (await this.action('experiment.button.save')).isDisabled());
  }

  async save(): Promise<void> {
    await (await this.action('experiment.button.save')).click();
  }

  async saveAndCreateNew(): Promise<void> {
    await (await this.action('experiment.button.saveAndCreate')).click();
  }

  async getOwnersOfNewExperiment(): Promise<{ disabled: boolean; hint: string } | null> {
    const field = await this.ownersOfNewExperiment();
    if (!field) return null;
    const [hint] = await field.getTextHints();
    return { disabled: await field.isDisabled(), hint };
  }

  private formField(field: ExperimentField): Promise<MatFormFieldHarness> {
    return this.locatorFor(MatFormFieldHarness.with({ floatingLabelText: FIELD_LABELS[field] }))();
  }

  private async input(field: ExperimentField): Promise<MatInputHarness> {
    const control = await (await this.formField(field)).getControl(MatInputHarness);
    if (!control) throw new Error(`no input in the ${field} field`);
    return control;
  }

  private action(label: string): Promise<MatButtonHarness> {
    return this.locatorFor(
      MatButtonHarness.with({ ancestor: 'mat-dialog-actions', text: label }),
    )();
  }
}
