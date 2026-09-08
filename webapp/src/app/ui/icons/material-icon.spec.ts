import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MaterialIcon } from './material-icon';

describe('MaterialIcon', () => {
  let fixture: ComponentFixture<MaterialIcon>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MaterialIcon],
    }).compileComponents();

    fixture = TestBed.createComponent(MaterialIcon);
    fixture.componentRef.setInput('ligature', 'settings');
    await fixture.whenStable();
  });

  it('renders the ligature as text of the Material Symbols font', () => {
    const host = fixture.elementRef.nativeElement as HTMLElement;

    expect(host.textContent?.trim()).toBe('settings');
    expect(host.classList).toContain('material-symbols-rounded');
  });
});
