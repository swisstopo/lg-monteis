import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ChartComponent } from './chart.component';
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
vi.mock('chartjs-plugin-zoom', () => ({
  default: { id: 'zoom' },
}));

vi.mock('chart.js', async (importOriginal) => {
  const actual: any = await importOriginal();

  class MockChart {
    // Intercepts Chart.register() called in chart-registry.ts
    static register = vi.fn();

    // Mirror the real Chart.js behaviour of retaining the config it was constructed with.
    config: any;
    data: any;
    options: any;

    constructor(_canvas: unknown, config: any) {
      this.config = config;
      this.data = config.data;
      this.options = config.options;
    }

    // Spies for lifecycle methods
    destroy = vi.fn();
    update = vi.fn();
    resetZoom = vi.fn();
  }

  return {
    ...actual,
    Chart: MockChart,
  };
});

describe('Chart', () => {
  let component: ChartComponent;
  let fixture: ComponentFixture<ChartComponent>;

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
      imports: [ChartComponent],
      providers: [provideTranslateService()],
    }).compileComponents();

    fixture = TestBed.createComponent(ChartComponent);
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

  describe('Interactions and Events', () => {
    it('should call resetZoom on the chart instance when resetZoom() is invoked', () => {
      const instance = (component as any).instance;
      instance.resetZoom = vi.fn();

      component.resetZoom();

      expect(instance.resetZoom).toHaveBeenCalled();
    });

    it('should not throw an error if resetZoom() is called before instance initialization', () => {
      const originalInstance = (component as any).instance;
      (component as any).instance = undefined;

      expect(() => component.resetZoom()).not.toThrow();

      (component as any).instance = originalInstance;
    });

    it('should emit pointClick when a valid chart element is clicked', () => {
      const emitSpy = vi.spyOn(component.pointClick, 'emit');

      const mockEvent = {} as any;
      const mockElements = [{ datasetIndex: 0, index: 1 }] as any[];

      (component as any).emitPointEvent(mockEvent, mockElements, component.pointClick);

      expect(emitSpy).toHaveBeenCalledWith({
        datasetId: 'sensor-1',
        datasetLabel: 'Sensor 1',
        point: { x: 1, y: 3 },
      });
    });

    it('should not emit if no chart element is interacted with', () => {
      const emitSpy = vi.spyOn(component.pointClick, 'emit');

      (component as any).emitPointEvent({} as any, [], component.pointClick);

      expect(emitSpy).not.toHaveBeenCalled();
    });

    it('should emit rangeSelected with the exact axis bounds when a drag-zoom selection completes', () => {
      const instance = (component as any).instance;
      const emitSpy = vi.spyOn(component.rangeSelected, 'emit');
      const mockChart = { scales: { x: { min: 5, max: 42 } }, resetZoom: vi.fn() };

      instance.options.plugins.zoom.zoom.onZoomComplete({ chart: mockChart });

      expect(emitSpy).toHaveBeenCalledWith({ min: 5, max: 42 });
      // No manual resetZoom(): forcing an extra full-dataset render here is what caused the
      // browser to stall while the (still large) pre-refetch dataset was still in place.
      expect(mockChart.resetZoom).not.toHaveBeenCalled();
    });
  });

  describe('Reactivity and Updates', () => {
    it('should update the chart instance when dataset inputs change', async () => {
      const instance = (component as any).instance;
      const updateSpy = vi.spyOn(instance, 'update');

      const newDataset: ChartDataset = {
        id: 'sensor-2',
        label: 'Sensor 2',
        data: [{ x: 10, y: 42 }],
        color: '#00ff00',
      };

      fixture.componentRef.setInput('datasets', [newDataset]);

      await fixture.whenStable();

      expect(updateSpy).toHaveBeenCalled();
      expect(instance.data.datasets).toHaveLength(1);
      expect(instance.data.datasets[0].label).toBe('Sensor 2');
      expect(instance.data.datasets[0].data).toEqual([{ x: 10, y: 42 }]);
    });

    it('should update the chart configuration when options inputs change', async () => {
      const instance = (component as any).instance;

      fixture.componentRef.setInput('options', { title: 'Updated Title' });

      await fixture.whenStable();

      expect(instance.options.plugins.title.text).toBe('Updated Title');
    });
  });
});
