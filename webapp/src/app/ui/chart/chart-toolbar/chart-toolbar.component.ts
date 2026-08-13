import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { TranslatePipe } from '@ngx-translate/core';

/**
 * Vertical toolbar for chart interactions: drag-to-zoom toggle, zoom in/out,
 * reset zoom, and download.
 */
@Component({
  selector: 'app-chart-toolbar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './chart-toolbar.component.html',
  styleUrl: './chart-toolbar.component.scss',
  imports: [MatIconButton, MatIcon, MatTooltip, TranslatePipe],
})
export class ChartToolbarComponent {
  readonly dragZoomEnabled = input<boolean>(true);

  readonly toggleDragZoom = output<void>();
  readonly zoomIn = output<void>();
  readonly zoomOut = output<void>();
  readonly resetZoom = output<void>();
  readonly download = output<void>();
}
