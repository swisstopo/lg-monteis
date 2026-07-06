import { ComponentFixture, TestBed } from '@angular/core/testing';

import { WorkbenchView } from '@scion/workbench';
import Dialog from './dialog';

describe('Dialog', () => {
  let component: Dialog;
  let fixture: ComponentFixture<Dialog>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Dialog],
      providers: [WorkbenchView],
    }).compileComponents();

    fixture = TestBed.createComponent(Dialog);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });
});
