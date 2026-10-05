import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, input, resource, signal } from '@angular/core';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { ExperimentDocumentResponseDto } from '@core/generated';
import { toErrorDtos } from '@core/http/api-error.model';
import { ToastService } from '@core/notifications/toast.service';
import { ExperimentDocumentService } from '@features/experiment/services/experiment-document.service';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { FileDownloadService } from '@ui/file-download/file-download.service';

@Component({
  selector: 'app-experiment-documents',
  imports: [DatePipe, MatButton, MatIconButton, MatIcon, MatProgressSpinner, TranslatePipe],
  templateUrl: './experiment-documents.html',
  styleUrl: './experiment-documents.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExperimentDocuments {
  private readonly documentService = inject(ExperimentDocumentService);
  private readonly fileDownloadService = inject(FileDownloadService);
  private readonly toastService = inject(ToastService);
  private readonly translateService = inject(TranslateService);

  readonly experimentId = input.required<string>();
  readonly readOnly = input(false);

  protected readonly uploading = signal(false);

  protected readonly documents = resource({
    params: () => this.experimentId(),
    loader: ({ params: experimentId }) => this.documentService.getDocuments(experimentId),
  });

  // one after the other, so each failed file gets its own toast
  protected async upload(fileInput: HTMLInputElement): Promise<void> {
    const files = Array.from(fileInput.files ?? []);
    fileInput.value = '';
    if (files.length === 0) return;

    this.uploading.set(true);
    try {
      for (const file of files) {
        await this.uploadFile(file);
      }
    } finally {
      this.uploading.set(false);
      this.documents.reload();
    }
  }

  protected download(document: ExperimentDocumentResponseDto): Promise<void> {
    return this.toastOnFailure(async () =>
      this.fileDownloadService.download(await this.contentOf(document), document.fileName!),
    );
  }

  protected view(document: ExperimentDocumentResponseDto): Promise<void> {
    return this.toastOnFailure(() =>
      this.fileDownloadService.openInNewTab(() => this.contentOf(document)),
    );
  }

  private async uploadFile(file: File): Promise<void> {
    try {
      await this.documentService.uploadDocument(this.experimentId(), file);
    } catch (error) {
      this.toastUploadErrors(error, file.name);
    }
  }

  // a body without message key (network error, 500) still gets the generic toast
  private toastUploadErrors(error: unknown, fileName: string): void {
    const messages = toErrorDtos(error).map((dto) =>
      this.translateService.translate(
        dto.messageKey ?? 'experiment.documents.error.upload',
        dto.params,
      )(),
    );
    if (messages.length === 0) {
      messages.push(this.translateService.translate('experiment.documents.error.upload')());
    }
    messages.forEach((message) => this.toastService.error(message, fileName));
  }

  private async toastOnFailure(action: () => Promise<void>): Promise<void> {
    try {
      await action();
    } catch {
      this.toastService.error(
        this.translateService.translate('experiment.documents.error.download')(),
      );
    }
  }

  private contentOf(document: ExperimentDocumentResponseDto): Promise<Blob> {
    return this.documentService.getContent(this.experimentId(), document.id!);
  }
}
