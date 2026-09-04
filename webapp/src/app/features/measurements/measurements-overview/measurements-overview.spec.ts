import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';

import MeasurementsOverview from './measurements-overview';

describe('MeasurementsOverview', () => {
  let component: MeasurementsOverview;
  let fixture: ComponentFixture<MeasurementsOverview>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MeasurementsOverview],
      providers: [provideTranslateService()],
    }).compileComponents();

    fixture = TestBed.createComponent(MeasurementsOverview);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
