import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TableHeader } from './table-header';

describe('TableHeader', () => {
  let component: TableHeader;
  let fixture: ComponentFixture<TableHeader>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TableHeader],
    }).compileComponents();

    fixture = TestBed.createComponent(TableHeader);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
