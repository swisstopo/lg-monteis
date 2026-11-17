import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, input, resource } from '@angular/core';
import {
  ExperimentControllerService,
  NotifyingParameterControllerService,
  NotifyingParameterResponseDto,
} from '@core/generated';
import { skipGlobalErrorToast } from '@core/http/http-context';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { WorkbenchView } from '@scion/workbench';
import { InlineError } from '@ui/inline-error/inline-error';
import Table from '@ui/table/table';
import { GridOptions } from 'ag-grid-community';
import { firstValueFrom } from 'rxjs';
import { createColumns } from './columns';

@Component({
  selector: 'app-notification-page',
  imports: [Table, TranslatePipe, InlineError],
  providers: [DatePipe],
  templateUrl: './notification-page.html',
  styleUrl: './notification-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class NotificationPage {
  private readonly notifyingParameterApi = inject(NotifyingParameterControllerService);
  private readonly experimentApi = inject(ExperimentControllerService);
  private readonly translateService = inject(TranslateService);

  readonly experimentId = input.required<string>();

  protected readonly columns = createColumns(inject(DatePipe));

  protected readonly gridOptions: GridOptions<NotifyingParameterResponseDto> = {
    rowSelection: undefined,
  };

  protected readonly parameters = resource({
    params: () => this.experimentId(),
    loader: ({ params: experimentId }) =>
      firstValueFrom(this.notifyingParameterApi.findNotifyingParametersOf(experimentId)),
  });

  // the parameter request already toasts an unknown id, a second toast would only repeat it
  private readonly experiment = resource({
    params: () => this.experimentId(),
    loader: ({ params: experimentId }) =>
      firstValueFrom(
        this.experimentApi.getExperiment(experimentId, 'body', false, {
          context: skipGlobalErrorToast(),
        }),
      ),
  });

  constructor(view: WorkbenchView) {
    effect(() => {
      const name = this.experiment.hasValue() ? (this.experiment.value().name ?? '') : '';
      view.title = this.translateService.translate('tab.notification', { name })();
    });
  }

  protected getRowId = (row: NotifyingParameterResponseDto): string => row.sensorParameterId ?? '';
}
