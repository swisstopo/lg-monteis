import { ComponentHarness } from '@angular/cdk/testing';
import { MatFormFieldHarness } from '@angular/material/form-field/testing';
import { MatSelectHarness } from '@angular/material/select/testing';

export class ExperimentOwnerPickerHarness extends ComponentHarness {
  static readonly hostSelector = 'app-experiment-owner-picker';

  private readonly formField = this.locatorFor(MatFormFieldHarness);
  private readonly select = this.locatorFor(MatSelectHarness);

  /** names in the closed select */
  async getOwnerNames(): Promise<string[]> {
    const text = await (await this.select()).getValueText();
    return text ? text.split(', ') : [];
  }

  async isDisabled(): Promise<boolean> {
    return (await this.select()).isDisabled();
  }

  async getOfferedOwners(): Promise<{ text: string; selected: boolean; disabled: boolean }[]> {
    const select = await this.select();
    await select.open();
    const options = await select.getOptions();
    const offered = await Promise.all(
      options.map(async (option) => ({
        text: squash(await option.getText()),
        selected: await option.isSelected(),
        disabled: await option.isDisabled(),
      })),
    );
    await select.close();
    return offered;
  }

  async selectOwner(name: string): Promise<void> {
    await this.toggleOwner(name, true);
  }

  async deselectOwner(name: string): Promise<void> {
    await this.toggleOwner(name, false);
  }

  async getHint(): Promise<string | undefined> {
    const [hint] = await (await this.formField()).getTextHints();
    return hint;
  }

  private async toggleOwner(name: string, selected: boolean): Promise<void> {
    const select = await this.select();
    await select.open();
    const [option] = await select.getOptions({ text: new RegExp(name) });
    if (!option) throw new Error(`no owner option ${name}`);
    if ((await option.isSelected()) !== selected) await option.click();
    await select.close();
  }
}

// the option template breaks the name and the email over several lines
function squash(text: string): string {
  return text.replace(/\s+/g, ' ').trim();
}
