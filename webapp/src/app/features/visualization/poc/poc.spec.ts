import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { WorkbenchView } from '@scion/workbench';

import Poc from './poc';

describe('Poc', () => {
  let component: Poc;
  let fixture: ComponentFixture<Poc>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Poc],
      providers: [WorkbenchView, provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(Poc);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
