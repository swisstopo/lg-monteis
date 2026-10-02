import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, input, resource, signal } from '@angular/core';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { ErrorDto, ExperimentDocumentResponseDto } from '@core/generated';
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

  protected async download(document: ExperimentDocumentResponseDto): Promise<void> {
    try {
      this.fileDownloadService.download(await this.contentOf(document), document.fileName!);
    } catch {
      this.toastDownloadFailed();
    }
  }

  protected async view(document: ExperimentDocumentResponseDto): Promise<void> {
    try {
      await this.fileDownloadService.openInNewTab(() => this.contentOf(document));
    } catch {
      this.toastDownloadFailed();
    }
  }

  // GLOBAL errors (403, 500) are already toasted by the restErrorInterceptor
  private async uploadFile(file: File): Promise<void> {
    try {
      await this.documentService.uploadDocument(this.experimentId(), file);
    } catch (error) {
      toErrorDtos(error)
        .filter((dto) => dto.target !== ErrorDto.TargetEnum.Global)
        .forEach((dto) =>
          this.toastService.error(
            this.translateService.translate(
              dto.messageKey ?? 'experiment.documents.error.upload',
              dto.params,
            )(),
            file.name,
          ),
        );
    }
  }

  private contentOf(document: ExperimentDocumentResponseDto): Promise<Blob> {
    return this.documentService.getContent(this.experimentId(), document.id!);
  }

  private toastDownloadFailed(): void {
    this.toastService.error(
      this.translateService.translate('experiment.documents.error.download')(),
    );
  }
}
