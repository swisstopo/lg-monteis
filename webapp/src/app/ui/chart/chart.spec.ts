import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import Chart from './chart';
import { ChartDataset } from './chart.types';

describe('Chart', () => {
  let component: Chart;
  let fixture: ComponentFixture<Chart>;

  const dataset: ChartDataset = {
    id: 'sensor-1',
    label: 'Sensor 1',
    data: [
      { x: 0, y: 1 },
      { x: 1, y: 3 },
    ],
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Chart],
    }).compileComponents();

    fixture = TestBed.createComponent(Chart);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('datasets', [dataset]);
    fixture.componentRef.setInput('labels', ['0', '1']);
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('renders a canvas element', () => {
    const canvas = fixture.nativeElement.querySelector('canvas');
    expect(canvas).toBeTruthy();
  });

  it('destroys the chart instance on destroy', () => {
    const instance = (component as unknown as { instance?: { destroy: () => void } }).instance;
    expect(instance).toBeTruthy();
    const destroySpy = instance
      ? (instance.destroy = vi.fn(instance.destroy.bind(instance)))
      : undefined;

    fixture.destroy();

    expect(destroySpy).toHaveBeenCalled();
  });
});
