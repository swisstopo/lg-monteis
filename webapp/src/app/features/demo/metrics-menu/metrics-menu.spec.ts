import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { WorkbenchStorage } from '@scion/workbench';
import MetricsMenu from './metrics-menu';

describe('MetricsMenu', () => {
  let fixture: ComponentFixture<MetricsMenu>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MetricsMenu],
      providers: [WorkbenchStorage, provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(MetricsMenu);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });
});
