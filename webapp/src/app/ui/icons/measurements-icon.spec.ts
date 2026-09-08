import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MeasurementsIcon } from './measurements-icon';

describe('MeasurementsIcon', () => {
  let fixture: ComponentFixture<MeasurementsIcon>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MeasurementsIcon],
    }).compileComponents();

    fixture = TestBed.createComponent(MeasurementsIcon);
    await fixture.whenStable();
  });

  it('renders an SVG adopting the host size and color', () => {
    const svg = (fixture.elementRef.nativeElement as HTMLElement).querySelector('svg');

    expect(svg).toBeTruthy();
    expect(svg?.getAttribute('fill')).toBe('currentColor');
    expect(svg?.getAttribute('width')).toBe('1em');
    expect(svg?.getAttribute('height')).toBe('1em');
  });
});
