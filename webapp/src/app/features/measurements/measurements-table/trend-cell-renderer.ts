import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { ChartPointDto, MeasurementResponseDto } from '@core/generated';
import { ChartPoint, ChartSparklineComponent } from '@ui/chart';
import { ICellRendererAngularComp } from 'ag-grid-angular';
import { ICellRendererParams } from 'ag-grid-community';

/**
 * Renders a {@link MeasurementResponseDto} row's `trend` (last 4 days of readings) as an inline
 * sparkline. Works unmodified under ag-grid's Infinite Row Model: cells (and therefore this
 * renderer) are created/destroyed by ag-grid as blocks scroll in and out, same as any other
 * cellRenderer; `refresh` covers the case where ag-grid reuses an existing instance for a
 * different row instead.
 */
@Component({
  selector: 'app-trend-cell-renderer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<app-chart-sparkline [data]="data()" />',
  styles: `
    :host {
      display: block;
      width: 100%;
      height: 28px;
    }
  `,
  imports: [ChartSparklineComponent],
})
export class TrendCellRenderer implements ICellRendererAngularComp {
  protected readonly data = signal<ChartPoint[]>([]);

  agInit(params: ICellRendererParams<MeasurementResponseDto, ChartPointDto[]>): void {
    this.data.set(toChartPoints(params.value));
  }

  refresh(params: ICellRendererParams<MeasurementResponseDto, ChartPointDto[]>): boolean {
    this.data.set(toChartPoints(params.value));
    return true;
  }
}

function toChartPoints(points: ChartPointDto[] | null | undefined): ChartPoint[] {
  return (points ?? [])
    .filter((point) => point.timestamp !== undefined && point.value !== undefined)
    .map((point) => ({ x: Date.parse(point.timestamp!), y: point.value! }));
}
