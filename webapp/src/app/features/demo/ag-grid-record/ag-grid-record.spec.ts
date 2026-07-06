import { ComponentFixture, TestBed } from '@angular/core/testing';
import { WorkbenchView } from '@scion/workbench';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { DemoControllerService } from '../../../core/generated';
import AgGridRecord from './ag-grid-record';

const demoServiceMock = {
  getMetrics: vi.fn().mockReturnValue(of([])),
};

describe('AgGridRecord', () => {
  let fixture: ComponentFixture<AgGridRecord>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AgGridRecord],
      providers: [
        {
          provide: DemoControllerService,
          useValue: demoServiceMock,
        },
        WorkbenchView,
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AgGridRecord);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });
});
