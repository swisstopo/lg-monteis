import { ComponentHarness } from '@angular/cdk/testing';

export class ListedOwnerHarness extends ComponentHarness {
  static hostSelector = '[data-testid="experiment-owner"]';

  private readonly name = this.locatorFor('span');
  private readonly mailLink = this.locatorForOptional('a');

  async getName(): Promise<string> {
    return (await this.name()).text();
  }

  async getMailto(): Promise<string | null> {
    const mailLink = await this.mailLink();
    return mailLink ? mailLink.getAttribute('href') : null;
  }
}

export class ExperimentOwnerListHarness extends ComponentHarness {
  static hostSelector = 'app-experiment-owner-list';

  private readonly owners = this.locatorForAll(ListedOwnerHarness);
  private readonly noOwners = this.locatorForOptional('[data-testid="no-owners"]');
  private readonly missingOwners = this.locatorForOptional('[data-testid="owners-missing"]');

  getOwners(): Promise<ListedOwnerHarness[]> {
    return this.owners();
  }

  async getOwnerNames(): Promise<string[]> {
    return Promise.all((await this.owners()).map((owner) => owner.getName()));
  }

  async saysNoOwner(): Promise<boolean> {
    return (await this.noOwners()) !== null;
  }

  async getMissingReason(): Promise<string | null> {
    const missingOwners = await this.missingOwners();
    return missingOwners ? missingOwners.text() : null;
  }
}
