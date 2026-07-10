import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormGroupDirective } from '@angular/forms';
import { WorkbenchView } from '@scion/workbench';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { FormulaResponseDto, SensorControllerService } from '../../../core/generated';
import SensorCreate from './sensor-create';

const sensorServiceMock = {
  findAllFormulas: vi.fn().mockReturnValue(of([])),
  createSensor: vi.fn(),
};

describe('SensorCreate', () => {
  let component: SensorCreate;
  let fixture: ComponentFixture<SensorCreate>;
  let mockFormDirective: FormGroupDirective;

  beforeEach(async () => {
    sensorServiceMock.findAllFormulas.mockReset().mockReturnValue(of([]));
    sensorServiceMock.createSensor.mockReset();

    mockFormDirective = {
      resetForm: vi.fn(),
    } as unknown as FormGroupDirective;

    await TestBed.configureTestingModule({
      imports: [SensorCreate],
      providers: [
        {
          provide: SensorControllerService,
          useValue: sensorServiceMock,
        },
        WorkbenchView,
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SensorCreate);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should pass down the full object properties when a formula is selected from the dropdown', () => {
    sensorServiceMock.createSensor.mockReturnValue(of({ status: 201, body: {} }));
    fixture.detectChanges();

    const selectedFormula: FormulaResponseDto = { id: 42, expression: 'x * 2', version: 1 };

    component['sensorForm'].patchValue({
      code: 'SN-01',
      name: 'Test Sensor',
      lowerBound: 0,
      upperBound: 100,
      formulaControl: selectedFormula as any,
    });

    component['onSubmit'](mockFormDirective);

    expect(sensorServiceMock.createSensor).toHaveBeenCalledWith(
      expect.objectContaining({
        formula: {
          id: 42,
          expression: 'x * 2',
          version: 1,
        },
      }),
      'response',
    );
  });

  it('should pass down a brand new trimmed string layout expression when text is typed manually', () => {
    sensorServiceMock.createSensor.mockReturnValue(of({ status: 201, body: {} }));
    fixture.detectChanges();

    component['sensorForm'].patchValue({
      code: 'SN-01',
      name: 'Test Sensor',
      lowerBound: 0,
      upperBound: 100,
      formulaControl: '   x + 10   ',
    });

    component['onSubmit'](mockFormDirective);

    expect(sensorServiceMock.createSensor).toHaveBeenCalledWith(
      expect.objectContaining({
        formula: {
          expression: 'x + 10',
        },
      }),
      'response',
    );
  });

  it('should map the formula sub-property to undefined when the form control field is blank spaces or empty', () => {
    sensorServiceMock.createSensor.mockReturnValue(of({ status: 201, body: {} }));
    fixture.detectChanges();

    component['sensorForm'].patchValue({
      code: 'SN-01',
      name: 'Test Sensor',
      lowerBound: 0,
      upperBound: 100,
      formulaControl: '    ',
    });

    component['onSubmit'](mockFormDirective);

    expect(sensorServiceMock.createSensor).toHaveBeenCalledWith(
      expect.objectContaining({
        formula: undefined,
      }),
      'response',
    );
  });

  it('should store the successful body payload, trigger cache reload, and invoke form reset', () => {
    const mockSuccessBody = { id: 99, code: 'SN-01', name: 'Test Sensor' };
    sensorServiceMock.createSensor.mockReturnValue(of({ status: 201, body: mockSuccessBody }));
    fixture.detectChanges();

    component['sensorForm'].patchValue({
      code: 'SN-01',
      name: 'Test Sensor',
      lowerBound: 0,
      upperBound: 100,
    });

    component['onSubmit'](mockFormDirective);

    fixture.detectChanges();

    expect(component['status']()).toBe(201);
    expect(component['serverResponse']()).toEqual(mockSuccessBody);
    expect(component['serverError']()).toBeNull();

    expect(sensorServiceMock.findAllFormulas).toHaveBeenCalledTimes(2);
    expect(mockFormDirective.resetForm).toHaveBeenCalled();
  });

  it('should securely register error payload attributes and prevent form resets upon network failures', () => {
    const mockErrorBody = { message: 'Database database primary key constraint failed.' };
    const networkError = new HttpErrorResponse({ status: 400, error: mockErrorBody });

    sensorServiceMock.createSensor.mockReturnValue(throwError(() => networkError));
    fixture.detectChanges();

    component['sensorForm'].patchValue({
      code: 'SN-01',
      name: 'Test Sensor',
      lowerBound: 0,
      upperBound: 100,
    });

    component['onSubmit'](mockFormDirective);

    expect(component['status']()).toBe(400);
    expect(component['serverError']()).toEqual(mockErrorBody);
    expect(component['serverResponse']()).toBeNull();
    expect(mockFormDirective.resetForm).not.toHaveBeenCalled();
  });
});
