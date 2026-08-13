import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNativeDateAdapter } from '@angular/material/core';
import { provideTranslateService } from '@ngx-translate/core';
import { WorkbenchView } from '@scion/workbench';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { OverviewControllerService } from '../../../core/generated';
import OverviewTable from './overview-table';

const overviewServiceMock = {
  getMetrics: vi.fn().mockReturnValue(of([])),
};

describe('OverviewTable', () => {
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
});
