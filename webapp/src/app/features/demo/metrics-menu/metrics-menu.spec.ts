import { ComponentFixture, TestBed } from '@angular/core/testing';

import MetricsMenu from './metrics-menu';

describe('MetricsMenu', () => {
  let component: MetricsMenu;
  let fixture: ComponentFixture<MetricsMenu>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MetricsMenu],
    }).compileComponents();

    fixture = TestBed.createComponent(MetricsMenu);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
