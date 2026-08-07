import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import Chart from './chart';
import { ChartDataset } from './chart.types';

vi.stubGlobal(
  'matchMedia',
  vi.fn().mockImplementation((query) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
);
// 2. Mock ResizeObserver (Chart.js needs this for responsive charts)
vi.stubGlobal(
  'ResizeObserver',
  vi.fn().mockImplementation(() => ({
    observe: vi.fn(),
    unobserve: vi.fn(),
    disconnect: vi.fn(),
  })),
);
vi.mock('chart.js', async (importOriginal) => {
  const actual: any = await importOriginal();

  class MockChart {
    // Intercepts Chart.register() called in chart-registry.ts
    static register = vi.fn();

    // Fake internal state
    config = { type: 'line' };
    data = { datasets: [], labels: [] };
    options = {};

    // Spies for lifecycle methods
    destroy = vi.fn();
    update = vi.fn();
  }

  return {
    ...actual,
    Chart: MockChart,
  };
});

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
      providers: [provideTranslateService()],
    }).compileComponents();

    fixture = TestBed.createComponent(Chart);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('title', 'TestChart');
    fixture.componentRef.setInput('datasets', [dataset]);
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
