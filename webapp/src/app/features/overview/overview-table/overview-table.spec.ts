import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNativeDateAdapter } from '@angular/material/core';
import { provideTranslateService } from '@ngx-translate/core';
import { WorkbenchView } from '@scion/workbench';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { ErrorDto, OverviewControllerService } from '../../../core/generated';
import { ToastService } from '../../../core/notifications/toast.service';
import { MesurementsService } from '../services/mesurements.service';
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

  it('shows a toast when the measurements service reports an unmapped error', () => {
    const mesurementsService = TestBed.inject(MesurementsService);
    const toastService = TestBed.inject(ToastService);

    mesurementsService.error.set([{ messageKey: 'chart.error.unspecified.message' } as ErrorDto]);
    fixture.detectChanges();

    expect(toastService.toasts()).toHaveLength(1);
  });

  it('closes any open chart dialog when an error is reported', () => {
    const mesurementsService = TestBed.inject(MesurementsService);
    const dialogSpy = vi.spyOn(fixture.componentInstance['dialog'], 'closeAll');

    mesurementsService.error.set([{ messageKey: 'chart.error.unspecified.message' } as ErrorDto]);
    fixture.detectChanges();

    expect(dialogSpy).toHaveBeenCalled();
  });
});
