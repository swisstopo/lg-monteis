import { ComponentFixture, TestBed } from '@angular/core/testing';
import { WorkbenchView } from '@scion/workbench';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { OverviewControllerService } from '../../../core/generated';
import SensorTable from './sensor-table';

const overviewServiceMock = {
  getMetrics: vi.fn().mockReturnValue(of([])),
};

describe('SensorTable', () => {
  let fixture: ComponentFixture<SensorTable>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SensorTable],
      providers: [
        {
          provide: OverviewControllerService,
          useValue: overviewServiceMock,
        },
        WorkbenchView,
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SensorTable);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });
});
