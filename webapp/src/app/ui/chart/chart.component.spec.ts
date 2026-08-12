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
    scales: any;

    constructor(_canvas: unknown, config: any) {
      this.config = config;
      this.data = config.data;
      this.options = config.options;
      this.scales = {};
    }

    // Spies for lifecycle methods
    destroy = vi.fn();
    update = vi.fn();
    resetZoom = vi.fn();
    zoom = vi.fn();
    toBase64Image = vi.fn().mockReturnValue('data:image/png;base64,mock');
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

  describe('Toolbar actions', () => {
    it('renders five toolbar icon buttons', () => {
      const buttons = fixture.nativeElement.querySelectorAll('.chart-toolbar button');
      expect(buttons).toHaveLength(5);
    });

    it('should emit a smaller range when zoomIn() is invoked', () => {
      const instance = (component as any).instance;
      instance.scales = { x: { min: 0, max: 100 } };
      (component as any).initialDateRange = { min: 0, max: 100 };
      const emitSpy = vi.spyOn(component.rangeSelected, 'emit');

      component.zoomIn();

      const emitted = emitSpy.mock.calls[0][0];
      expect(emitted.min).toBeGreaterThan(0);
      expect(emitted.max).toBeLessThan(100);
    });

    it('should emit a larger range when zoomOut() is invoked', () => {
      const instance = (component as any).instance;
      instance.scales = { x: { min: 10, max: 90 } };
      (component as any).initialDateRange = { min: 0, max: 100 };
      const emitSpy = vi.spyOn(component.rangeSelected, 'emit');

      component.zoomOut();

      expect(emitSpy).toHaveBeenCalledWith({ min: 6, max: 94 });
    });

    it('should reset the chart and clear Y ranges when the toolbar reset button is clicked', () => {
      const instance = (component as any).instance;
      instance.resetZoom = vi.fn();
      (component as any).manualYRanges = { y: { min: 10, max: 100 } };

      component.resetZoom();

      expect(instance.resetZoom).toHaveBeenCalled();
      expect((component as any).manualYRanges).toEqual({});
    });

    it('should toggle the drag-zoom mode when toggleDragZoom() is invoked', () => {
      const instance = (component as any).instance;
      instance.update = vi.fn();
      expect(instance.options.plugins.zoom.zoom.drag.enabled).toBe(true);

      component.toggleDragZoom();

      expect(component.dragZoomEnabled()).toBe(false);
      expect(instance.options.plugins.zoom.zoom.drag.enabled).toBe(false);
      expect(instance.update).toHaveBeenCalledWith('none');
    });

    it('should download the chart as a PNG when downloadChart() is invoked', () => {
      const instance = (component as any).instance;
      const createElementSpy = vi.spyOn(document, 'createElement');
      const clickSpy = vi.fn();
      createElementSpy.mockReturnValue({
        href: '',
        download: '',
        click: clickSpy,
      } as unknown as HTMLAnchorElement);

      try {
        component.downloadChart();

        expect(instance.toBase64Image).toHaveBeenCalledWith('image/png');
        expect(createElementSpy).toHaveBeenCalledWith('a');
        expect(clickSpy).toHaveBeenCalled();
      } finally {
        createElementSpy.mockRestore();
      }
    });
  });

  describe('Y-axis zoom persistence', () => {
    it('should store the initial date range when the chart first renders unzoomed data', async () => {
      const initialRange = (component as any).initialDateRange;
      expect(initialRange).toEqual({ min: 0, max: 1 });
    });

    it('should not update the initial date range when a zoomed refetch occurs', async () => {
      const instance = (component as any).instance;
      const mockChart = {
        scales: {
          x: { min: 5, max: 42 },
          y: { id: 'y', axis: 'y', min: 10, max: 100 },
        },
      };

      instance.options.plugins.zoom.zoom.onZoomComplete({ chart: mockChart });

      const refetchedDataset: ChartDataset = {
        id: 'sensor-1',
        label: 'Sensor 1',
        data: [{ x: 10, y: 20 }],
      };

      fixture.componentRef.setInput('datasets', [refetchedDataset]);
      await fixture.whenStable();

      expect((component as any).initialDateRange).toEqual({ min: 0, max: 1 });
    });

    it('should update the initial date range after reset and a new full dataset', async () => {
      const instance = (component as any).instance;
      const zoomedChart = {
        scales: {
          x: { min: 5, max: 42 },
          y: { id: 'y', axis: 'y', min: 10, max: 100 },
        },
      };

      instance.options.plugins.zoom.zoom.onZoomComplete({ chart: zoomedChart });

      const unzoomedChart = {
        scales: {
          x: { min: 0, max: 100 },
          y: { id: 'y', axis: 'y', min: 1, max: 200 },
        },
      };
      instance.resetZoom = vi.fn(() => {
        instance.options.plugins.zoom.zoom.onZoomComplete({ chart: unzoomedChart });
      });
      component.resetZoom();

      const newDataset: ChartDataset = {
        id: 'sensor-2',
        label: 'Sensor 2',
        data: [{ x: 100, y: 200 }],
      };

      fixture.componentRef.setInput('datasets', [newDataset]);
      await fixture.whenStable();

      expect((component as any).initialDateRange).toEqual({ min: 100, max: 100 });
    });
    it('should store the Y-axis zoom range when a drag-zoom selection completes', () => {
      const instance = (component as any).instance;
      const mockChart = {
        scales: {
          x: { min: 5, max: 42 },
          y: { id: 'y', axis: 'y', min: 10, max: 100 },
        },
        resetZoom: vi.fn(),
      };

      instance.options.plugins.zoom.zoom.onZoomComplete({ chart: mockChart });

      expect((component as any).manualYRanges).toEqual({
        y: { min: 10, max: 100 },
      });
    });

    it('should store multiple Y-axis zoom ranges keyed by axis id', () => {
      const instance = (component as any).instance;
      const mockChart = {
        scales: {
          x: { min: 5, max: 42 },
          y: { id: 'y', axis: 'y', min: 10, max: 100 },
          y2: { id: 'y2', axis: 'y', min: -50, max: 50 },
        },
        resetZoom: vi.fn(),
      };

      instance.options.plugins.zoom.zoom.onZoomComplete({ chart: mockChart });

      expect((component as any).manualYRanges).toEqual({
        y: { min: 10, max: 100 },
        y2: { min: -50, max: 50 },
      });
    });

    it('should apply stored Y-axis ranges to new chart scales when datasets change', async () => {
      const instance = (component as any).instance;
      const mockChart = {
        scales: {
          x: { min: 5, max: 42 },
          y: { id: 'y', axis: 'y', min: 10, max: 100 },
        },
        resetZoom: vi.fn(),
      };

      instance.options.plugins.zoom.zoom.onZoomComplete({ chart: mockChart });

      const newDataset: ChartDataset = {
        id: 'sensor-2',
        label: 'Sensor 2',
        data: [{ x: 10, y: 42 }],
        color: '#00ff00',
      };

      fixture.componentRef.setInput('datasets', [newDataset]);
      await fixture.whenStable();

      expect(instance.options.scales.y.min).toBe(10);
      expect(instance.options.scales.y.max).toBe(100);
    });

    it('should keep applying Y-axis ranges across subsequent dataset updates', async () => {
      const instance = (component as any).instance;
      const mockChart = {
        scales: {
          x: { min: 5, max: 42 },
          y: { id: 'y', axis: 'y', min: 10, max: 100 },
        },
        resetZoom: vi.fn(),
      };

      instance.options.plugins.zoom.zoom.onZoomComplete({ chart: mockChart });

      const datasetA: ChartDataset = { id: 'a', label: 'A', data: [{ x: 1, y: 2 }] };
      const datasetB: ChartDataset = { id: 'b', label: 'B', data: [{ x: 3, y: 4 }] };

      fixture.componentRef.setInput('datasets', [datasetA]);
      await fixture.whenStable();
      expect(instance.options.scales.y.min).toBe(10);
      expect(instance.options.scales.y.max).toBe(100);

      fixture.componentRef.setInput('datasets', [datasetB]);
      await fixture.whenStable();
      expect(instance.options.scales.y.min).toBe(10);
      expect(instance.options.scales.y.max).toBe(100);
    });

    it('should ignore stored Y-axis ranges for scales that no longer exist', async () => {
      const instance = (component as any).instance;
      (component as any).manualYRanges = {
        y: { min: 10, max: 100 },
        missing: { min: 0, max: 1 },
      };

      const newDataset: ChartDataset = {
        id: 'sensor-2',
        label: 'Sensor 2',
        data: [{ x: 10, y: 42 }],
        color: '#00ff00',
      };

      fixture.componentRef.setInput('datasets', [newDataset]);
      await fixture.whenStable();

      expect(instance.options.scales.y.min).toBe(10);
      expect(instance.options.scales.y.max).toBe(100);
      expect(instance.options.scales.missing).toBeUndefined();
    });

    it('should clear manual Y ranges and reset the chart when resetZoom is called', () => {
      const instance = (component as any).instance;
      const emitSpy = vi.spyOn(component.rangeSelected, 'emit');
      const zoomedChart = {
        scales: {
          x: { min: 5, max: 42 },
          y: { id: 'y', axis: 'y', min: 10, max: 100 },
        },
      };

      instance.options.plugins.zoom.zoom.onZoomComplete({ chart: zoomedChart });
      expect((component as any).manualYRanges).toEqual({
        y: { min: 10, max: 100 },
      });

      const unzoomedChart = {
        scales: {
          x: { min: 0, max: 100 },
          y: { id: 'y', axis: 'y', min: 1, max: 200 },
        },
      };
      instance.resetZoom = vi.fn(() => {
        instance.options.plugins.zoom.zoom.onZoomComplete({ chart: unzoomedChart });
      });

      component.resetZoom();

      expect(emitSpy).toHaveBeenLastCalledWith((component as any).initialDateRange);
      expect((component as any).manualYRanges).toEqual({});
      expect(instance.resetZoom).toHaveBeenCalled();
    });

    it('should not re-apply old Y ranges after resetZoom and a dataset update', async () => {
      const instance = (component as any).instance;
      const zoomedChart = {
        scales: {
          x: { min: 5, max: 42 },
          y: { id: 'y', axis: 'y', min: 10, max: 100 },
        },
      };

      instance.options.plugins.zoom.zoom.onZoomComplete({ chart: zoomedChart });

      const unzoomedChart = {
        scales: {
          x: { min: 0, max: 100 },
          y: { id: 'y', axis: 'y', min: 1, max: 200 },
        },
      };
      instance.resetZoom = vi.fn(() => {
        instance.options.plugins.zoom.zoom.onZoomComplete({ chart: unzoomedChart });
      });
      component.resetZoom();

      const newDataset: ChartDataset = {
        id: 'sensor-2',
        label: 'Sensor 2',
        data: [{ x: 10, y: 42 }],
        color: '#00ff00',
      };

      fixture.componentRef.setInput('datasets', [newDataset]);
      await fixture.whenStable();

      expect(instance.options.scales.y.min).toBeUndefined();
      expect(instance.options.scales.y.max).toBeUndefined();
    });

    it('should not emit rangeSelected or persist Y ranges when the X scale is missing', () => {
      const instance = (component as any).instance;
      const emitSpy = vi.spyOn(component.rangeSelected, 'emit');
      const mockChart = { scales: {}, resetZoom: vi.fn() };

      instance.options.plugins.zoom.zoom.onZoomComplete({ chart: mockChart });

      expect(emitSpy).not.toHaveBeenCalled();
      expect((component as any).manualYRanges).toEqual({});
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
