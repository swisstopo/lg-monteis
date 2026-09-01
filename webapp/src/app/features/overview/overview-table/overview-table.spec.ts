import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNativeDateAdapter } from '@angular/material/core';
import { provideTranslateService } from '@ngx-translate/core';
import { WorkbenchView } from '@scion/workbench';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { OverviewControllerService, ReadSimpleMetricDto } from '../../../core/generated';
import OverviewTable from './overview-table';

const overviewServiceMock = {
  getMetrics: vi.fn().mockReturnValue(of([])),
};

describe('OverviewTable', () => {
  let fixture: ComponentFixture<OverviewTable>;

  beforeEach(async () => {
    overviewServiceMock.getMetrics.mockReturnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [OverviewTable],
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

    fixture = TestBed.createComponent(OverviewTable);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('derives distinct, defined sensor ids from the metrics resource', async () => {
    const metrics: ReadSimpleMetricDto[] = [{ id: 1 }, { id: 2 }, { id: 2 }, { id: undefined }];
    overviewServiceMock.getMetrics.mockReturnValue(of(metrics));

    const component = TestBed.createComponent(OverviewTable).componentInstance as unknown as {
      metricsResource: { value: () => ReadSimpleMetricDto[] | undefined };
      sensorIds: () => number[];
    };

    await vi.waitFor(() => expect(component.metricsResource.value()).toBeDefined());

    expect(component.sensorIds()).toEqual([1, 2]);
  });
});
