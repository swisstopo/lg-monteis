import { ComponentFixture, TestBed } from '@angular/core/testing';
import { InlineError } from './inline-error';

describe('InlineError', () => {
  let component: InlineError;
  let fixture: ComponentFixture<InlineError>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InlineError],
    }).compileComponents();

    fixture = TestBed.createComponent(InlineError);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('message', 'An error occurred');
    await fixture.whenStable();
  });

  it('renders given error message', () => {
    expect(fixture.elementRef.nativeElement.textContent?.trim()).toBe('An error occurred');
  });
});
