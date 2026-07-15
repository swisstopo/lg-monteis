import { ComponentFixture, TestBed } from '@angular/core/testing';
import { WorkbenchView } from '@scion/workbench';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { OverviewControllerService } from '../../../core/generated';
import AgMatTable from './ag-mat-table';

const overviewServiceMock = {
  getMetrics: vi.fn().mockReturnValue(of([])),
};

describe('AgMatTable', () => {
  let fixture: ComponentFixture<AgMatTable>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AgMatTable],
      providers: [
        {
          provide: OverviewControllerService,
          useValue: overviewServiceMock,
        },
        WorkbenchView,
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AgMatTable);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });
});
