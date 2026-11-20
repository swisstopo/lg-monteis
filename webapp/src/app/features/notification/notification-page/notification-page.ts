import { DatePipe } from '@angular/common';
import { HttpErrorResponse, HttpStatusCode } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  resource,
} from '@angular/core';
import {
  ExperimentControllerService,
  NotifyingParameterControllerService,
  NotifyingParameterResponseDto,
} from '@core/generated';
import { skipGlobalErrorToast } from '@core/http/http-context';
import { RecipientsSection } from '@features/notification/recipients-section/recipients-section';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { WorkbenchView } from '@scion/workbench';
import { InlineError } from '@ui/inline-error/inline-error';
import Table from '@ui/table/table';
import { GridOptions } from 'ag-grid-community';
import { firstValueFrom } from 'rxjs';
import { createColumns } from './columns';

@Component({
  selector: 'app-notification-page',
  imports: [Table, TranslatePipe, InlineError, RecipientsSection],
  providers: [DatePipe],
  templateUrl: './notification-page.html',
  styleUrl: './notification-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class NotificationPage {
  private readonly notifyingParameterApi = inject(NotifyingParameterControllerService);
  private readonly experimentApi = inject(ExperimentControllerService);
  private readonly translateService = inject(TranslateService);
  private readonly view = inject(WorkbenchView);

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

  // unknown id => interceptor toast is enough, inline error would repeat it
  protected readonly loadFailed = computed(() => {
    const error = this.parameters.error();
    return (
      !!error && !(error instanceof HttpErrorResponse && error.status === HttpStatusCode.NotFound)
    );
  });

  // parameter request already toasts unknown id, 2nd toast would repeat it
  private readonly experiment = resource({
    params: () => this.experimentId(),
    loader: ({ params: experimentId }) =>
      firstValueFrom(
        this.experimentApi.getExperiment(experimentId, 'body', false, {
          context: skipGlobalErrorToast(),
        }),
      ),
  });

  constructor() {
    effect(() => {
      const name = this.experiment.hasValue() ? (this.experiment.value().name ?? '') : '';
      this.view.title = this.translateService.translate('tab.notification', { name })().trim();
    });
  }

  protected getRowId = (row: NotifyingParameterResponseDto): string => row.sensorParameterId ?? '';
}
