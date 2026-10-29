import { Component, input, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { By } from '@angular/platform-browser';
import { PermissionsService } from '@core/auth/permissions.service';
import { SensorResponseDto } from '@core/generated';
import { ToastService } from '@core/notifications/toast.service';
import { ExperimentService } from '@features/experiment/services/experiment.service';
import { SensorService } from '@features/sensor/services/sensor.service';
import { provideTranslateService } from '@ngx-translate/core';
import { Documents } from '@ui/documents/documents';
import { describe, expect, it, vi } from 'vitest';
import SensorEdit from './sensor-edit';

@Component({ selector: 'app-documents', template: '' })
class DocumentsStub {
  readonly parentId = input.required<string>();
  readonly readOnly = input(false);
}

const SENSOR: SensorResponseDto = {
  id: 'sensor-1',
  name: 'Sensor',
  dasSensorAlias: 'ALIAS',
  das: SensorResponseDto.DasEnum.SolExperts,
  coordinates: { x: 0, y: 0, z: 0 },
  parameters: [],
  canUploadDocuments: false,
};

interface Setup {
  sensorId?: string;
  viewOnly?: boolean;
  isAdmin?: boolean;
  sensor?: SensorResponseDto;
}

async function setup({ sensorId, viewOnly = false, isAdmin = false, sensor = SENSOR }: Setup) {
  const emptyList = { value: () => [], reload: vi.fn() };
  TestBed.configureTestingModule({
    imports: [SensorEdit],
    providers: [
      provideTranslateService(),
      {
        provide: SensorService,
        useValue: {
          getSensor: vi.fn(),
          sensor: { value: () => (sensorId ? sensor : undefined), error: () => undefined },
          allFormulas: emptyList,
          allTypes: emptyList,
          error: signal(undefined),
        },
      },
      { provide: ExperimentService, useValue: { allExperiments: emptyList } },
      { provide: PermissionsService, useValue: { isAdmin: () => isAdmin } },
      { provide: ToastService, useValue: { error: vi.fn(), success: vi.fn() } },
      { provide: MatDialogRef, useValue: { close: vi.fn() } },
    ],
  }).overrideComponent(SensorEdit, {
    remove: { imports: [Documents] },
    add: { imports: [DocumentsStub] },
  });

  const fixture = TestBed.createComponent(SensorEdit);
  if (sensorId) {
    fixture.componentRef.setInput('sensorId', sensorId);
    fixture.componentRef.setInput('viewOnly', viewOnly);
  }
  await fixture.whenStable();
  const element = fixture.nativeElement as HTMLElement;
  return {
    element,
    documents: () =>
      fixture.debugElement.query(By.directive(DocumentsStub))?.componentInstance as
        DocumentsStub | undefined,
    nameInput: () => element.querySelector<HTMLInputElement>('input[matinput]'),
  };
}

describe('SensorEdit', () => {
  it('shows no documents while a sensor is created', async () => {
    const { documents } = await setup({ isAdmin: true });

    expect(documents()).toBeUndefined();
  });

  it('lets an admin edit the sensor', async () => {
    const { element, nameInput } = await setup({ sensorId: 'sensor-1', isAdmin: true });

    expect(nameInput()?.disabled).toBe(false);
    expect(element.textContent).toContain('sensor.parameter.addButton');
  });

  it('opens read-only for a user who is no admin, whatever the caller asked for', async () => {
    const { element, nameInput } = await setup({ sensorId: 'sensor-1' });

    expect(nameInput()?.disabled).toBe(true);
    expect(element.textContent).not.toContain('sensor.parameter.addButton');
    expect(element.textContent).toContain('sensor.edit.title.view');
  });

  it('opens read-only for an admin who asked to view', async () => {
    const { nameInput } = await setup({ sensorId: 'sensor-1', viewOnly: true, isAdmin: true });

    expect(nameInput()?.disabled).toBe(true);
  });

  it('lists the documents of the sensor, read-only without upload permission', async () => {
    const { documents } = await setup({ sensorId: 'sensor-1' });

    expect(documents()?.parentId()).toBe('sensor-1');
    expect(documents()?.readOnly()).toBe(true);
  });

  it('lets a pi upload documents in the read-only dialog', async () => {
    const { documents, nameInput } = await setup({
      sensorId: 'sensor-1',
      sensor: { ...SENSOR, canUploadDocuments: true },
    });

    expect(nameInput()?.disabled).toBe(true);
    expect(documents()?.readOnly()).toBe(false);
  });
});
