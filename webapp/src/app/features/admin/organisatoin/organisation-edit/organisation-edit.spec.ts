import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { ErrorDto, OrganisationResponseDto } from '@core/generated';
import { ToastService } from '@core/notifications/toast.service';
import { OrganisationService } from '@features/admin/organisatoin/services/organisation.service';
import { provideTranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import OrganisationEdit from './organisation-edit';

const ORGANISATION: OrganisationResponseDto = {
  id: 'organisation-1',
  name: 'swisstopo',
  comment: 'Operator',
};

describe('OrganisationEdit', () => {
  let organisationService: {
    getOrganisation: ReturnType<typeof vi.fn>;
    saveOrganisation: ReturnType<typeof vi.fn>;
  };
  let dialogRef: { close: ReturnType<typeof vi.fn> };
  let toast: { success: ReturnType<typeof vi.fn>; error: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    organisationService = {
      getOrganisation: vi.fn().mockResolvedValue(ORGANISATION),
      saveOrganisation: vi.fn().mockResolvedValue(ORGANISATION),
    };
    dialogRef = { close: vi.fn() };
    toast = { success: vi.fn(), error: vi.fn() };
    TestBed.configureTestingModule({
      imports: [OrganisationEdit],
      providers: [
        provideTranslateService(),
        { provide: OrganisationService, useValue: organisationService },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: ToastService, useValue: toast },
      ],
    });
  });

  async function render(organisationId?: string) {
    const fixture = TestBed.createComponent(OrganisationEdit);
    if (organisationId) {
      fixture.componentRef.setInput('organisationId', organisationId);
    }
    await fixture.whenStable();
    return new DialogView(fixture);
  }

  describe('create', () => {
    it('starts empty', async () => {
      const view = await render();

      expect(view.title()).toBe('organisation.edit.title.create');
      expect(view.input('name').value).toBe('');
      expect(organisationService.getOrganisation).not.toHaveBeenCalled();
    });

    it('cannot be saved while the form is invalid', async () => {
      const view = await render();

      expect(view.button('organisation.button.save').disabled).toBe(true);
      expect(view.button('organisation.button.saveAndCreate').disabled).toBe(true);
    });

    it('creates the trimmed organisation without a blank comment and closes', async () => {
      const view = await render();
      await view.type('name', '  ETH Zurich ');
      await view.type('comment', '   ');

      await view.click('organisation.button.save');

      await vi.waitFor(() => expect(dialogRef.close).toHaveBeenCalled());
      expect(organisationService.saveOrganisation).toHaveBeenCalledWith(undefined, {
        name: 'ETH Zurich',
        comment: undefined,
      });
      expect(toast.success).toHaveBeenCalledWith('organisation.success');
    });

    it('does not count blanks around the name towards its min length', async () => {
      const view = await render();

      await view.type('name', 'a ');

      expect(view.button('organisation.button.save').disabled).toBe(true);
    });

    it('stays open after "save and create new", ready for the next organisation', async () => {
      const view = await render();
      await view.type('name', 'ETH Zurich');

      await view.click('organisation.button.saveAndCreate');

      await vi.waitFor(() => expect(view.input('name').value).toBe(''));
      expect(dialogRef.close).not.toHaveBeenCalled();
      expect(toast.success).toHaveBeenCalled();
    });

    it('shows a server error on its field and stays open', async () => {
      organisationService.saveOrganisation.mockRejectedValue(
        new HttpErrorResponse({
          status: 422,
          error: [
            {
              field: 'name',
              messageKey: 'validation.unique',
              target: ErrorDto.TargetEnum.Field,
            },
          ],
        }),
      );
      const view = await render();
      await view.type('name', 'swisstopo');

      await view.click('organisation.button.save');

      await vi.waitFor(() =>
        expect(view.element.querySelector('mat-error')?.textContent).toContain('validation.unique'),
      );
      expect(dialogRef.close).not.toHaveBeenCalled();
    });
  });

  describe('edit', () => {
    it('loads the organisation into the form', async () => {
      const view = await render('organisation-1');

      expect(organisationService.getOrganisation).toHaveBeenCalledWith('organisation-1');
      expect(view.title()).toBe('organisation.edit.title.edit');
      await vi.waitFor(() => expect(view.input('name').value).toBe('swisstopo'));
      expect(view.input('comment').value).toBe('Operator');
    });

    it('updates the organisation with its id', async () => {
      const view = await render('organisation-1');
      await vi.waitFor(() => expect(view.input('name').value).toBe('swisstopo'));
      await view.type('name', 'swisstopo 2');

      await view.click('organisation.button.save');

      await vi.waitFor(() => expect(dialogRef.close).toHaveBeenCalled());
      expect(organisationService.saveOrganisation).toHaveBeenCalledWith('organisation-1', {
        name: 'swisstopo 2',
        comment: 'Operator',
      });
    });

    it('offers no "save and create new"', async () => {
      const view = await render('organisation-1');

      expect(view.buttonLabels()).toEqual(['button.cancel', 'organisation.button.save']);
    });
  });
});

const LABELS = {
  name: 'organisation.name.label',
  comment: 'organisation.comment.label',
} as const;

class DialogView {
  readonly element: HTMLElement;

  constructor(private readonly fixture: ComponentFixture<OrganisationEdit>) {
    this.element = fixture.nativeElement as HTMLElement;
  }

  title(): string | undefined {
    return this.element.querySelector('h2')?.textContent?.trim();
  }

  input(field: keyof typeof LABELS): HTMLInputElement {
    const formField = [...this.element.querySelectorAll('mat-form-field')].find((candidate) =>
      candidate.querySelector('mat-label')?.textContent?.includes(LABELS[field]),
    );
    return formField!.querySelector<HTMLInputElement>('input, textarea')!;
  }

  buttonLabels(): string[] {
    return this.actionButtons().map(labelOf);
  }

  button(label: string): HTMLButtonElement {
    return this.actionButtons().find((button) => labelOf(button) === label)!;
  }

  private actionButtons(): HTMLButtonElement[] {
    return [...this.element.querySelectorAll<HTMLButtonElement>('mat-dialog-actions button')];
  }

  async type(field: keyof typeof LABELS, value: string): Promise<void> {
    const input = this.input(field);
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await this.fixture.whenStable();
  }

  async click(label: string): Promise<void> {
    this.button(label).click();
    await this.fixture.whenStable();
  }
}

// the icons render through fontIcon classes, the button's text is its label alone
function labelOf(button: HTMLButtonElement): string {
  return button.textContent?.trim() ?? '';
}
