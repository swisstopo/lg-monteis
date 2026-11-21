import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { NotifyingParameterResponseDto } from '@core/generated';
import { TranslatePipe } from '@ngx-translate/core';
import { ICellRendererAngularComp } from 'ag-grid-angular';
import { ICellRendererParams } from 'ag-grid-community';

@Component({
  selector: 'app-sensor-parameter-cell-renderer',
  imports: [TranslatePipe],
  template: `
    <span>{{ label() }}</span>
    @if (inactive()) {
      <span class="inactive-tag" data-testid="inactive">{{
        'notification.parameters.inactive' | translate
      }}</span>
    }
  `,
  styles: `
    .inactive-tag {
      margin-left: 0.5rem;
      padding: 0 0.4rem;
      border-radius: 0.25rem;
      border: 1px solid currentColor;
      font-size: 0.85em;
      opacity: 0.7;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SensorParameterCellRenderer implements ICellRendererAngularComp {
  protected readonly label = signal('');
  protected readonly inactive = signal(false);

  agInit(params: ICellRendererParams<NotifyingParameterResponseDto, string>): void {
    this.refresh(params);
  }

  refresh(params: ICellRendererParams<NotifyingParameterResponseDto, string>): boolean {
    this.label.set(params.value ?? '');
    this.inactive.set(
      params.data?.sensorActive === false || params.data?.sensorParameterActive === false,
    );
    return true;
  }
}
