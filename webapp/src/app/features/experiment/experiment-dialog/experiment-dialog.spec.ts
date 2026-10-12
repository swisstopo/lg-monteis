import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, input, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { By } from '@angular/platform-browser';
import { PermissionsService } from '@core/auth/permissions.service';
import { ErrorDto, ExperimentResponseDto } from '@core/generated';
import { ToastService } from '@core/notifications/toast.service';
import { OrganisationService } from '@features/admin/organisatoin/services/organisation.service';
import { ExperimentDocuments } from '@features/experiment/experiment-documents/experiment-documents';
import { ExperimentService } from '@features/experiment/services/experiment.service';
import {
  EXPERIMENT_FIELDS,
  ExperimentDialogHarness,
} from '@features/experiment/testing/experiment-dialog.harness';
import { ALICE, anExperiment, BOB } from '@features/experiment/testing/experiment-fixtures';
import { provideTranslateService } from '@ngx-translate/core';
import { describe, expect, it, Mocked, vi } from 'vitest';
import { ExperimentDialog } from './experiment-dialog';

@Component({ selector: 'app-experiment-documents', template: '' })
class ExperimentDocumentsStub {
  readonly experimentId = input.required<string>();
  readonly readOnly = input(false);
}

interface Setup {
  experimentId?: string;
  viewOnly?: boolean;
  canWrite?: boolean;
  isAdmin?: boolean;
  experiment?: ExperimentResponseDto;
}

async function setup({
  experimentId,
  viewOnly = false,
  canWrite = true,
  isAdmin = false,
  experiment = anExperiment(),
}: Setup = {}) {
  const experimentService: Mocked<
    Pick<ExperimentService, 'getExperiment' | 'saveExperiment' | 'findOwnerCandidatesForExperiment'>
  > = {
    getExperiment: vi.fn().mockResolvedValue(experiment),
    saveExperiment: vi.fn().mockResolvedValue(experiment),
    findOwnerCandidatesForExperiment: vi.fn().mockResolvedValue([ALICE, BOB]),
  };
  const toast: Mocked<Pick<ToastService, 'success' | 'warning' | 'error'>> = {
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
  };
  const dialogRef: Mocked<Pick<MatDialogRef<ExperimentDialog>, 'close'>> = { close: vi.fn() };

  TestBed.configureTestingModule({
    imports: [ExperimentDialog],
    providers: [
      provideTranslateService(),
      { provide: ExperimentService, useValue: experimentService },
      { provide: OrganisationService, useValue: { allOrganisations: signal([]) } },
      {
        provide: PermissionsService,
        useValue: { canWriteExperiment: () => canWrite, isAdmin: () => isAdmin },
      },
      { provide: MatDialogRef, useValue: dialogRef },
      { provide: ToastService, useValue: toast },
    ],
  }).overrideComponent(ExperimentDialog, {
    remove: { imports: [ExperimentDocuments] },
    add: { imports: [ExperimentDocumentsStub] },
  });

  const fixture = TestBed.createComponent(ExperimentDialog);
  if (experimentId) {
    fixture.componentRef.setInput('experimentId', experimentId);
    fixture.componentRef.setInput('viewOnly', viewOnly);
  }
  const dialog = await TestbedHarnessEnvironment.harnessForFixture(
    fixture,
    ExperimentDialogHarness,
  );
  const documents = () =>
    fixture.debugElement.query(By.directive(ExperimentDocumentsStub))?.componentInstance as
      ExperimentDocumentsStub | undefined;

  return { dialog, documents, experimentService, toast, dialogRef };
}

function saveFailsWith(field: string, messageKey: string) {
  return new HttpErrorResponse({
    status: 422,
    error: [{ field, messageKey, target: ErrorDto.TargetEnum.Field }],
  });
}

describe('ExperimentDialog', () => {
  describe('create', () => {
    it('starts empty, without documents', async () => {
      const { dialog, documents, experimentService } = await setup();

      expect(await dialog.getTitle()).toBe('experiment.dialog.title.create');
      expect(await dialog.getValue('name')).toBe('');
      expect(documents()).toBeUndefined();
      expect(experimentService.getExperiment).not.toHaveBeenCalled();
    });

    it('shows the owners disabled and explains that they come later', async () => {
      const { dialog } = await setup({ isAdmin: true });

      expect(await dialog.getOwnersOfNewExperiment()).toEqual({
        disabled: true,
        hint: 'experiment.owners.createHint',
      });
      expect(await dialog.ownerPicker()).toBeNull();
      expect(await dialog.ownerList()).toBeNull();
    });

    it('cannot be saved while the form is invalid', async () => {
      const { dialog } = await setup();

      expect(await dialog.canSave()).toBe(false);
    });

    it('creates the experiment and closes', async () => {
      const { dialog, experimentService, toast, dialogRef } = await setup();
      await dialog.enter('name', 'Mont Terri Beta');

      await dialog.save();

      await vi.waitFor(() => expect(dialogRef.close).toHaveBeenCalled());
      expect(experimentService.saveExperiment).toHaveBeenCalledWith(
        expect.objectContaining({ id: undefined, name: 'Mont Terri Beta' }),
      );
      expect(toast.success).toHaveBeenCalledWith('experiment.success');
    });

    it('stays open after "save and create new", ready for the next experiment', async () => {
      const { dialog, toast, dialogRef } = await setup();
      await dialog.enter('name', 'Mont Terri Beta');

      await dialog.saveAndCreateNew();

      await vi.waitFor(async () => expect(await dialog.getValue('name')).toBe(''));
      expect(dialogRef.close).not.toHaveBeenCalled();
      expect(toast.success).toHaveBeenCalled();
    });

    it('shows why a too long comment blocks saving', async () => {
      const { dialog } = await setup();
      await dialog.enter('name', 'Mont Terri Beta');

      await dialog.enter('comment', 'x'.repeat(4097));

      expect(await dialog.canSave()).toBe(false);
      expect(await dialog.getErrors('comment')).toEqual([
        'experiment.comment.validation.maxLength',
      ]);
    });

    it('shows a server error on its field and stays open', async () => {
      const { dialog, experimentService, dialogRef } = await setup();
      experimentService.saveExperiment.mockRejectedValue(
        saveFailsWith('name', 'experiment.name.validation.unique'),
      );
      await dialog.enter('name', 'Mont Terri Alpha');

      await dialog.save();

      await vi.waitFor(async () =>
        expect(await dialog.getErrors('name')).toEqual(['experiment.name.validation.unique']),
      );
      expect(dialogRef.close).not.toHaveBeenCalled();
    });
  });

  describe('edit', () => {
    it('loads the experiment into the form', async () => {
      const { dialog, experimentService } = await setup({ experimentId: 'experiment-1' });

      expect(experimentService.getExperiment).toHaveBeenCalledWith('experiment-1');
      expect(await dialog.getTitle()).toBe('experiment.dialog.title.edit');
      expect(await dialog.getValue('name')).toBe('Mont Terri Alpha');
      expect(await dialog.getValue('comment')).toBe('borehole');
      expect(await dialog.isDisabled('name')).toBe(false);
    });

    it('shows the documents with upload', async () => {
      const { documents } = await setup({ experimentId: 'experiment-1' });

      expect(documents()?.experimentId()).toBe('experiment-1');
      expect(documents()?.readOnly()).toBe(false);
    });

    it('updates the experiment with its id and version', async () => {
      const { dialog, experimentService, dialogRef } = await setup({
        experimentId: 'experiment-1',
      });
      await dialog.enter('name', 'Mont Terri Alpha 2');

      await dialog.save();

      await vi.waitFor(() => expect(dialogRef.close).toHaveBeenCalled());
      expect(experimentService.saveExperiment).toHaveBeenCalledWith(
        expect.objectContaining({ id: 'experiment-1', version: 3, name: 'Mont Terri Alpha 2' }),
      );
    });

    it('creates the next experiment after "save and create new"', async () => {
      const { dialog, experimentService } = await setup({ experimentId: 'experiment-1' });
      await dialog.saveAndCreateNew();
      await vi.waitFor(async () => expect(await dialog.getValue('name')).toBe(''));

      await dialog.enter('name', 'Mont Terri Gamma');
      await dialog.save();

      await vi.waitFor(() => expect(experimentService.saveExperiment).toHaveBeenCalledTimes(2));
      expect(experimentService.saveExperiment).toHaveBeenLastCalledWith(
        expect.objectContaining({ id: undefined, name: 'Mont Terri Gamma' }),
      );
    });
  });

  describe('owners', () => {
    const asAdmin = { experimentId: 'experiment-1', isAdmin: true };

    it('lets an admin pick the owners, starting with the stored ones', async () => {
      const { dialog } = await setup(asAdmin);

      const picker = await dialog.ownerPicker();

      expect(await picker?.getOwnerNames()).toEqual(['Alice Muster']);
      expect(await dialog.ownerList()).toBeNull();
    });

    it('saves changed owners with the experiment', async () => {
      const { dialog, experimentService, toast, dialogRef } = await setup(asAdmin);
      experimentService.saveExperiment.mockResolvedValue(anExperiment({ owners: [ALICE, BOB] }));
      await (await dialog.ownerPicker())!.selectOwner('Bob Beispiel');

      await dialog.save();

      await vi.waitFor(() => expect(dialogRef.close).toHaveBeenCalled());
      expect(experimentService.saveExperiment).toHaveBeenCalledWith(
        expect.objectContaining({ ownerIds: ['alice', 'bob'] }),
      );
      expect(toast.success).toHaveBeenCalledWith('experiment.success');
    });

    it('leaves unchanged owners out of the save', async () => {
      const { dialog, experimentService, dialogRef } = await setup(asAdmin);

      await dialog.save();

      await vi.waitFor(() => expect(dialogRef.close).toHaveBeenCalled());
      expect(experimentService.saveExperiment).toHaveBeenCalledWith(
        expect.objectContaining({ ownerIds: undefined }),
      );
    });

    it('closes with a warning when Keycloak kept the owners from being changed', async () => {
      const { dialog, experimentService, toast, dialogRef } = await setup(asAdmin);
      experimentService.saveExperiment.mockResolvedValue(
        anExperiment({ owners: [], ownersStatus: 'KEYCLOAK_UNAVAILABLE' }),
      );
      await (await dialog.ownerPicker())!.deselectOwner('Alice Muster');

      await dialog.save();

      await vi.waitFor(() => expect(dialogRef.close).toHaveBeenCalled());
      expect(toast.warning).toHaveBeenCalledWith('experiment.owners.notSaved');
      expect(toast.success).not.toHaveBeenCalled();
    });

    it('stays open with the error at the owners when one is no PI', async () => {
      const { dialog, experimentService, toast, dialogRef } = await setup(asAdmin);
      experimentService.saveExperiment.mockRejectedValue(
        saveFailsWith('ownerIds', 'experiment.owner.not-eligible'),
      );
      const picker = (await dialog.ownerPicker())!;
      await picker.selectOwner('Bob Beispiel');

      await dialog.save();

      await vi.waitFor(async () =>
        expect(await picker.getHint()).toBe('experiment.owner.not-eligible'),
      );
      expect(await picker.getOwnerNames()).toEqual(['Alice Muster', 'Bob Beispiel']);
      expect(dialogRef.close).not.toHaveBeenCalled();
      expect(toast.error).not.toHaveBeenCalled();
    });

    it('shows a PI who is no admin the owners read-only', async () => {
      const { dialog } = await setup({ experimentId: 'experiment-1' });

      expect(await (await dialog.ownerList())?.getOwnerNames()).toEqual(['Alice Muster']);
      expect(await dialog.ownerPicker()).toBeNull();
    });

    it('tells a reader why the owners are missing', async () => {
      const { dialog } = await setup({
        experimentId: 'experiment-1',
        canWrite: false,
        experiment: anExperiment({ owners: [], ownersStatus: 'KEYCLOAK_UNAVAILABLE' }),
      });

      expect(await (await dialog.ownerList())?.getMissingReason()).toBe(
        'experiment.owners.status.KEYCLOAK_UNAVAILABLE',
      );
    });

    it('does not let an admin edit owners it cannot see', async () => {
      const { dialog, experimentService, dialogRef } = await setup({
        ...asAdmin,
        experiment: anExperiment({ owners: [], ownersStatus: 'KEYCLOAK_UNAVAILABLE' }),
      });
      expect(await dialog.ownerPicker()).toBeNull();
      expect(await dialog.ownerList()).not.toBeNull();

      await dialog.save();

      await vi.waitFor(() => expect(dialogRef.close).toHaveBeenCalled());
      expect(experimentService.saveExperiment).toHaveBeenCalledWith(
        expect.objectContaining({ ownerIds: undefined }),
      );
    });
  });

  describe('view', () => {
    const asReader = { experimentId: 'experiment-1', canWrite: false };

    it('shows the experiment read-only', async () => {
      const { dialog } = await setup(asReader);

      expect(await dialog.getTitle()).toBe('experiment.dialog.title.view');
      expect(await dialog.getValue('name')).toBe('Mont Terri Alpha');
      for (const field of EXPERIMENT_FIELDS) {
        expect(await dialog.isDisabled(field)).toBe(true);
      }
      expect(await dialog.areDatepickersDisabled()).toBe(true);
    });

    it('stays read-only when opened for viewing, even for a writer', async () => {
      const { dialog } = await setup({ experimentId: 'experiment-1', viewOnly: true });

      expect(await dialog.getTitle()).toBe('experiment.dialog.title.view');
      expect(await dialog.isDisabled('name')).toBe(true);
      expect(await dialog.getActionLabels()).toEqual(['button.close']);
    });

    it('offers only Close', async () => {
      const { dialog } = await setup(asReader);

      expect(await dialog.getActionLabels()).toEqual(['button.close']);
    });

    it('shows the documents without upload', async () => {
      const { documents } = await setup(asReader);

      expect(documents()?.readOnly()).toBe(true);
    });
  });
});
