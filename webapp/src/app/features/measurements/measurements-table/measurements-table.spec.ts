import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNativeDateAdapter } from '@angular/material/core';
import { OverviewControllerService, ReadSimpleMetricDto } from '@core/generated';
import { provideTranslateService } from '@ngx-translate/core';
import { WorkbenchView } from '@scion/workbench';
import { of } from 'rxjs';
import { vi } from 'vitest';
import MeasurementsTable from './measurements-table';

const overviewServiceMock = {
  getMetrics: vi.fn().mockReturnValue(of([])),
};

describe('MeasurementsTable', () => {
  let fixture: ComponentFixture<MeasurementsTable>;

  beforeEach(async () => {
    overviewServiceMock.getMetrics.mockReturnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [MeasurementsTable],
      providers: [
        {
          provide: OverviewControllerService,
          useValue: overviewServiceMock,
        },
        WorkbenchView,
        provideTranslateService(),
        provideNativeDateAdapter(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(MeasurementsTable);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('derives distinct, defined sensor ids from the selected rows only', () => {
    const component = TestBed.createComponent(MeasurementsTable).componentInstance as unknown as {
      selectedRows: { set: (rows: ReadSimpleMetricDto[]) => void };
      selectedSensorIds: () => string[];
    };

    component.selectedRows.set([
      { metadataSensorId: '1' },
      { metadataSensorId: '2' },
      { metadataSensorId: '2' },
      { metadataSensorId: undefined },
    ]);

    expect(component.selectedSensorIds()).toEqual(['1', '2']);
  });
});
