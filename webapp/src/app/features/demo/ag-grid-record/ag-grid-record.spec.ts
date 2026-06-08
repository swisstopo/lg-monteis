import { ComponentFixture, TestBed } from '@angular/core/testing';

import AgGridRecord from './ag-grid-record';

describe('AgGridRecord', () => {
  let component: AgGridRecord;
  let fixture: ComponentFixture<AgGridRecord>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AgGridRecord],
    }).compileComponents();

    fixture = TestBed.createComponent(AgGridRecord);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
