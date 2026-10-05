import { HttpErrorResponse } from '@angular/common/http';
import { Component, input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { PermissionsService } from '@core/auth/permissions.service';
import { ErrorDto, ExperimentResponseDto } from '@core/generated';
import { ToastService } from '@core/notifications/toast.service';
import { ExperimentDocuments } from '@features/experiment/experiment-documents/experiment-documents';
import { ExperimentService } from '@features/experiment/services/experiment.service';
import { provideTranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ExperimentDialog, openExperimentDialog } from './experiment-dialog';

const EXPERIMENT: ExperimentResponseDto = {
  id: 'experiment-1',
  version: 3,
  name: 'Mont Terri Alpha',
  comment: 'borehole',
  period: { start: '2030-01-01', end: '2030-05-05' },
};

/** Stands in for the documents section, which has its own spec. */
@Component({ selector: 'app-experiment-documents', template: '' })
class ExperimentDocumentsStub {
  experimentId = input.required<string>();
  readOnly = input(false);
}

describe('ExperimentDialog', () => {
  let experimentService: {
    getExperiment: ReturnType<typeof vi.fn>;
    saveExperiment: ReturnType<typeof vi.fn>;
  };
  let canWrite: boolean;
  let dialogRef: { close: ReturnType<typeof vi.fn> };
  let toast: { success: ReturnType<typeof vi.fn>; error: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    experimentService = {
      getExperiment: vi.fn().mockResolvedValue(EXPERIMENT),
      saveExperiment: vi.fn().mockResolvedValue(EXPERIMENT),
    };
    canWrite = true;
    dialogRef = { close: vi.fn() };
    toast = { success: vi.fn(), error: vi.fn() };
    TestBed.configureTestingModule({
      imports: [ExperimentDialog],
      providers: [
        provideTranslateService(),
        { provide: ExperimentService, useValue: experimentService },
        { provide: PermissionsService, useValue: { canWriteExperiment: () => canWrite } },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: ToastService, useValue: toast },
      ],
    });
    TestBed.overrideComponent(ExperimentDialog, {
      remove: { imports: [ExperimentDocuments] },
      add: { imports: [ExperimentDocumentsStub] },
    });
  });

  async function render(experimentId?: string) {
    const fixture = TestBed.createComponent(ExperimentDialog);
    if (experimentId) {
      fixture.componentRef.setInput('experimentId', experimentId);
    }
    await fixture.whenStable();
    return new DialogView(fixture);
  }

  describe('create', () => {
    it('starts empty, without documents', async () => {
      const view = await render();

      expect(view.title()).toBe('experiment.dialog.title.create');
      expect(view.input('name').value).toBe('');
      expect(view.documents()).toBeNull();
      expect(experimentService.getExperiment).not.toHaveBeenCalled();
    });

    it('cannot be saved while the form is invalid', async () => {
      const view = await render();

      expect(view.button('experiment.button.save').disabled).toBe(true);
      expect(view.button('experiment.button.saveAndCreate').disabled).toBe(true);
    });

    it('creates the experiment and closes', async () => {
      const view = await render();
      await view.type('name', 'Mont Terri Beta');

      await view.click('experiment.button.save');

      await vi.waitFor(() => expect(dialogRef.close).toHaveBeenCalled());
      const payload = experimentService.saveExperiment.mock.calls[0][0];
      expect(payload.id).toBeUndefined();
      expect(payload.name).toBe('Mont Terri Beta');
      expect(toast.success).toHaveBeenCalledWith('experiment.success');
    });

    it('stays open after "save and create new", ready for the next experiment', async () => {
      const view = await render();
      await view.type('name', 'Mont Terri Beta');

      await view.click('experiment.button.saveAndCreate');

      await vi.waitFor(() => expect(view.input('name').value).toBe(''));
      expect(dialogRef.close).not.toHaveBeenCalled();
      expect(toast.success).toHaveBeenCalled();
    });

    it('shows why a too long comment blocks saving', async () => {
      const view = await render();
      await view.type('name', 'Mont Terri Beta');

      await view.type('comment', 'x'.repeat(4097));
      view.input('comment').dispatchEvent(new Event('blur'));
      await view.settle();

      expect(view.button('experiment.button.save').disabled).toBe(true);
      expect(view.element.querySelector('mat-error')?.textContent).toContain(
        'experiment.comment.validation.maxLength',
      );
    });

    it('shows a server error on its field and stays open', async () => {
      experimentService.saveExperiment.mockRejectedValue(
        new HttpErrorResponse({
          status: 400,
          error: [
            {
              field: 'name',
              messageKey: 'experiment.name.validation.unique',
              target: ErrorDto.TargetEnum.Field,
            },
          ],
        }),
      );
      const view = await render();
      await view.type('name', 'Mont Terri Alpha');

      await view.click('experiment.button.save');

      await vi.waitFor(() =>
        expect(view.element.querySelector('mat-error')?.textContent).toContain(
          'experiment.name.validation.unique',
        ),
      );
      expect(dialogRef.close).not.toHaveBeenCalled();
    });
  });

  describe('edit', () => {
    it('loads the experiment into the form', async () => {
      const view = await render('experiment-1');

      expect(experimentService.getExperiment).toHaveBeenCalledWith('experiment-1');
      expect(view.title()).toBe('experiment.dialog.title.edit');
      await vi.waitFor(() => expect(view.input('name').value).toBe('Mont Terri Alpha'));
      expect(view.input('comment').value).toBe('borehole');
      expect(view.input('name').disabled).toBe(false);
    });

    it('shows the documents with upload', async () => {
      const view = await render('experiment-1');

      expect(view.documents()?.experimentId()).toBe('experiment-1');
      expect(view.documents()?.readOnly()).toBe(false);
    });

    it('updates the experiment with its id and version', async () => {
      const view = await render('experiment-1');
      await vi.waitFor(() => expect(view.input('name').value).toBe('Mont Terri Alpha'));
      await view.type('name', 'Mont Terri Alpha 2');

      await view.click('experiment.button.save');

      await vi.waitFor(() => expect(dialogRef.close).toHaveBeenCalled());
      expect(experimentService.saveExperiment).toHaveBeenCalledWith(
        expect.objectContaining({ id: 'experiment-1', version: 3, name: 'Mont Terri Alpha 2' }),
      );
    });

    it('creates the next experiment after "save and create new"', async () => {
      const view = await render('experiment-1');
      await vi.waitFor(() => expect(view.input('name').value).toBe('Mont Terri Alpha'));
      await view.click('experiment.button.saveAndCreate');
      await vi.waitFor(() => expect(view.input('name').value).toBe(''));

      await view.type('name', 'Mont Terri Gamma');
      await view.click('experiment.button.save');

      await vi.waitFor(() => expect(experimentService.saveExperiment).toHaveBeenCalledTimes(2));
      expect(experimentService.saveExperiment.mock.calls[1][0].id).toBeUndefined();
    });
  });

  describe('view', () => {
    beforeEach(() => {
      canWrite = false;
    });

    it('shows the experiment read-only', async () => {
      const view = await render('experiment-1');

      expect(view.title()).toBe('experiment.dialog.title.view');
      await vi.waitFor(() => expect(view.input('name').value).toBe('Mont Terri Alpha'));
      for (const field of ['name', 'comment', 'start', 'end'] as const) {
        expect(view.input(field).disabled).toBe(true);
      }
      expect(view.element.querySelectorAll('mat-datepicker-toggle button[disabled]')).toHaveLength(
        2,
      );
    });

    it('offers only Close', async () => {
      const view = await render('experiment-1');

      expect(view.buttonLabels()).toEqual(['experiment.button.close']);
    });

    it('shows the documents without upload', async () => {
      const view = await render('experiment-1');

      expect(view.documents()?.readOnly()).toBe(true);
    });
  });
});

describe('openExperimentDialog', () => {
  it('opens the dialog for a new experiment', () => {
    const dialog = { open: vi.fn() };

    openExperimentDialog(dialog as unknown as MatDialog);

    expect(dialog.open).toHaveBeenCalledWith(
      ExperimentDialog,
      expect.objectContaining({ bindings: [] }),
    );
  });

  it('binds the experiment id', () => {
    const dialog = { open: vi.fn() };

    openExperimentDialog(dialog as unknown as MatDialog, 'experiment-1');

    expect(dialog.open.mock.calls[0][1].bindings).toHaveLength(1);
  });
});

const LABELS = {
  name: 'experiment.name.label',
  start: 'experiment.period.start.label',
  end: 'experiment.period.end.label',
  comment: 'experiment.comment.label',
} as const;

class DialogView {
  readonly element: HTMLElement;

  constructor(private readonly fixture: ComponentFixture<ExperimentDialog>) {
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

  documents(): ExperimentDocumentsStub | null {
    const debug = this.fixture.debugElement.query(
      (node) => node.componentInstance instanceof ExperimentDocumentsStub,
    );
    return debug?.componentInstance ?? null;
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

  async settle(): Promise<void> {
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
