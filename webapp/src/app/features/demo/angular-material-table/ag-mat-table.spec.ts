import { ComponentFixture, TestBed } from '@angular/core/testing';

import AgMatTable from './ag-mat-table';

describe('AgMatTable', () => {
  let component: AgMatTable;
  let fixture: ComponentFixture<AgMatTable>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AgMatTable],
    }).compileComponents();

    fixture = TestBed.createComponent(AgMatTable);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
