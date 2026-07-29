import { ComponentFixture, TestBed } from '@angular/core/testing';
import { WorkbenchView } from '@scion/workbench';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { OverviewControllerService } from '../../../core/generated';
import OverviewTable from './overview-table';

const overviewServiceMock = {
  getMetrics: vi.fn().mockReturnValue(of([])),
};

describe('SensorTable', () => {
  let fixture: ComponentFixture<OverviewTable>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OverviewTable],
      providers: [
        {
          provide: OverviewControllerService,
          useValue: overviewServiceMock,
        },
        WorkbenchView,
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OverviewTable);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });
});
