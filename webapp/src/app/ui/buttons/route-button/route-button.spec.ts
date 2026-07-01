import { ComponentFixture, TestBed } from '@angular/core/testing';

import { provideRouter } from '@angular/router';
import { WorkbenchStorage } from '@scion/workbench';
import { RouteButton } from './route-button';

describe('RouteButton', () => {
  let fixture: ComponentFixture<RouteButton>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RouteButton],
      providers: [provideRouter([]), WorkbenchStorage],
    }).compileComponents();

    fixture = TestBed.createComponent(RouteButton);
    fixture.componentRef.setInput('label', 'test');
    fixture.componentRef.setInput('route', ['test']);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });
});
