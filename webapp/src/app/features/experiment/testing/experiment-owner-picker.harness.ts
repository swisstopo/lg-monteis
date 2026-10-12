import { ComponentHarness } from '@angular/cdk/testing';
import { MatAutocompleteHarness } from '@angular/material/autocomplete/testing';
import { MatChipGridHarness, MatChipInputHarness } from '@angular/material/chips/testing';
import { MatFormFieldHarness } from '@angular/material/form-field/testing';

export class ExperimentOwnerPickerHarness extends ComponentHarness {
  static hostSelector = 'app-experiment-owner-picker';

  private readonly formField = this.locatorFor(MatFormFieldHarness);
  private readonly chips = this.locatorFor(MatChipGridHarness);
  private readonly search = this.locatorFor(MatChipInputHarness);
  private readonly autocomplete = this.locatorFor(MatAutocompleteHarness);

  async getOwnerNames(): Promise<string[]> {
    const rows = await (await this.chips()).getRows();
    return Promise.all(rows.map((row) => row.getText()));
  }

  async removeOwner(name: string): Promise<void> {
    const [row] = await (await this.chips()).getRows({ text: name });
    await (await row.getRemoveButton()).click();
  }

  async searchFor(text: string): Promise<void> {
    await (await this.search()).setValue(text);
  }

  async getSearchText(): Promise<string> {
    return (await this.search()).getValue();
  }

  async isSearchDisabled(): Promise<boolean> {
    return (await this.search()).isDisabled();
  }

  async getOfferedOwners(): Promise<string[]> {
    const autocomplete = await this.autocomplete();
    await autocomplete.focus();
    const options = await autocomplete.getOptions();
    return Promise.all(options.map(async (option) => squash(await option.getText())));
  }

  async addOwner(name: string): Promise<void> {
    const autocomplete = await this.autocomplete();
    await autocomplete.focus();
    await autocomplete.selectOption({ text: new RegExp(name) });
  }

  async getHint(): Promise<string | undefined> {
    const [hint] = await (await this.formField()).getTextHints();
    return hint;
  }
}

// the option template breaks the name and the email over several lines
function squash(text: string): string {
  return text.replace(/\s+/g, ' ').trim();
}
