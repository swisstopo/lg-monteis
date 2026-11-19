import { HttpErrorResponse, HttpStatusCode } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  resource,
} from '@angular/core';
import { MatChipsModule } from '@angular/material/chips';
import { RecipientControllerService, RecipientDto } from '@core/generated';
import { skipGlobalErrorToast } from '@core/http/http-context';
import { TranslatePipe } from '@ngx-translate/core';
import { InlineError } from '@ui/inline-error/inline-error';
import { firstValueFrom } from 'rxjs';

@Component({
  selector: 'app-recipients-section',
  imports: [MatChipsModule, TranslatePipe, InlineError],
  templateUrl: './recipients-section.html',
  styleUrl: './recipients-section.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RecipientsSection {
  private readonly recipientApi = inject(RecipientControllerService);

  readonly experimentId = input.required<string>();

  // parameter request already toasts unknown id, 2nd toast would repeat it
  protected readonly recipients = resource({
    params: () => this.experimentId(),
    loader: ({ params: experimentId }) =>
      firstValueFrom(
        this.recipientApi.findRecipientsOf(experimentId, 'body', false, {
          context: skipGlobalErrorToast(),
        }),
      ),
  });

  protected readonly loadFailed = computed(() => {
    const error = this.recipients.error();
    return (
      !!error && !(error instanceof HttpErrorResponse && error.status === HttpStatusCode.NotFound)
    );
  });

  protected displayName(recipient: RecipientDto): string {
    const name = [recipient.firstName, recipient.lastName]
      .filter((part) => !!part?.trim())
      .join(' ');
    return name || recipient.email || recipient.id;
  }
}
