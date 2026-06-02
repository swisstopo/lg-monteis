import { ComponentFixture, TestBed } from '@angular/core/testing';

import CdsTable from './cds-table';

describe('CdsTable', () => {
  let component: CdsTable;
  let fixture: ComponentFixture<CdsTable>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CdsTable],
    }).compileComponents();

    fixture = TestBed.createComponent(CdsTable);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
