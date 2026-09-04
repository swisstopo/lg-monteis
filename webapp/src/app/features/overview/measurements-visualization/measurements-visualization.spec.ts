import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { WorkbenchView } from '@scion/workbench';
import MeasurementsVisualization from './measurements-visualization';

describe('MeasurementsVisualization', () => {
  let component: MeasurementsVisualization;
  let fixture: ComponentFixture<MeasurementsVisualization>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MeasurementsVisualization],
      providers: [WorkbenchView, provideTranslateService()],
    }).compileComponents();

    fixture = TestBed.createComponent(MeasurementsVisualization);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
