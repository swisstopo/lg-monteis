import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  linkedSignal,
  resource,
  signal,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectChange, MatSelectModule } from '@angular/material/select';
import { DirectoryUserDto, RecipientControllerService } from '@core/generated';
import { toErrorDtos } from '@core/http/api-error.model';
import { skipGlobalErrorToast } from '@core/http/http-context';
import { ToastService } from '@core/notifications/toast.service';
import { UserNamePipe } from '@core/user-directory/user-name';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';

@Component({
  selector: 'app-recipients-section',
  imports: [
    MatButtonModule,
    MatChipsModule,
    MatFormFieldModule,
    MatIconModule,
    MatSelectModule,
    TranslatePipe,
    UserNamePipe,
  ],
  templateUrl: './recipients-section.html',
  styleUrl: './recipients-section.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RecipientsSection {
  private readonly recipientApi = inject(RecipientControllerService);
  private readonly toastService = inject(ToastService);
  private readonly translateService = inject(TranslateService);

  readonly experimentId = input.required<string>();

  // the section shows the failure itself
  protected readonly recipients = resource({
    params: () => this.experimentId(),
    loader: ({ params: experimentId }) =>
      firstValueFrom(
        this.recipientApi.findRecipientsOf(experimentId, 'body', false, {
          context: skipGlobalErrorToast(),
        }),
      ),
  });

  // denied and unavailable ask for different things, a misconfigured Keycloak needs an admin
  protected readonly errorKey = computed(() => {
    const error = this.recipients.error();
    if (!error) return undefined;
    return toErrorDtos(error)[0]?.messageKey ?? 'error.user-directory.unavailable';
  });

  protected readonly selected = linkedSignal<DirectoryUserDto[]>(() =>
    this.recipients.hasValue()
      ? this.recipients
          .value()
          .candidates.filter((candidate) => candidate.selected)
          .map((candidate) => candidate.user)
      : [],
  );

  protected readonly offered = computed(() => {
    if (!this.recipients.hasValue()) return [];
    const selectedIds = new Set(this.selected().map((recipient) => recipient.id));
    return this.recipients
      .value()
      .candidates.map((candidate) => candidate.user)
      .filter((user) => !selectedIds.has(user.id));
  });

  protected readonly saving = signal(false);

  protected add(event: MatSelectChange<DirectoryUserDto>): void {
    this.selected.update((selected) => [...selected, event.value]);
    // select only picks, the chips hold the list
    event.source.value = undefined;
  }

  protected remove(recipient: DirectoryUserDto): void {
    this.selected.update((selected) => selected.filter(({ id }) => id !== recipient.id));
  }

  protected async save(): Promise<void> {
    this.saving.set(true);
    try {
      const stored = await firstValueFrom(
        this.recipientApi.replaceRecipientsOf(this.experimentId(), {
          userIds: this.selected().map((recipient) => recipient.id),
        }),
      );
      this.recipients.set(stored);
      this.toastService.success(
        this.translateService.translate('notification.recipients.success')(),
      );
    } catch {
      // restErrorInterceptor toasts the failure, edits stay for a retry
    } finally {
      this.saving.set(false);
    }
  }
}
