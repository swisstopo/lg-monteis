import { DatePipe } from '@angular/common';
import { Component, computed, inject, input, resource, signal } from '@angular/core';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { PermissionsService } from '@core/auth/permissions.service';
import { ErrorDto, ExperimentDocumentResponseDto } from '@core/generated';
import { toErrorDtos } from '@core/http/api-error.model';
import { ToastService } from '@core/notifications/toast.service';
import { ExperimentDocumentService } from '@features/experiment/services/experiment-document.service';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { CsvDownloadService } from '@ui/table/csv-download.service';

@Component({
  selector: 'app-experiment-documents',
  imports: [DatePipe, MatButton, MatIconButton, MatIcon, MatProgressSpinner, TranslatePipe],
  templateUrl: './experiment-documents.html',
  styleUrl: './experiment-documents.scss',
})
export class ExperimentDocuments {
  private readonly documentService = inject(ExperimentDocumentService);
  private readonly permissions = inject(PermissionsService);
  private readonly toastService = inject(ToastService);
  private readonly translateService = inject(TranslateService);
  private readonly downloadService = inject(CsvDownloadService);

  readonly experimentId = input.required<string>();

  protected readonly canUpload = computed(() =>
    this.permissions.canWriteExperiment(this.experimentId()),
  );
  protected readonly uploading = signal(false);

  protected readonly documents = resource({
    params: () => ({ id: this.experimentId() }),
    loader: ({ params }) => this.documentService.getDocuments(params.id),
  });

  async onFilesSelected(input: HTMLInputElement) {
    const files = Array.from(input.files ?? []);
    input.value = '';
    if (files.length === 0) return;

    this.uploading.set(true);
    try {
      for (const file of files) {
        await this.upload(file);
      }
    } finally {
      this.uploading.set(false);
      this.documents.reload();
    }
  }

  async onDownload(document: ExperimentDocumentResponseDto) {
    try {
      const blob = await this.documentService.getContent(this.experimentId(), document.id!);
      this.downloadService.download(blob, document.fileName!);
    } catch {
      this.toastService.error(
        this.translateService.translate('experiment.documents.error.download')(),
      );
    }
  }

  // the tab is opened before the await, browsers block a popup that is no longer tied to the click
  async onView(document: ExperimentDocumentResponseDto) {
    const tab = window.open('', '_blank');
    try {
      const blob = await this.documentService.getContent(this.experimentId(), document.id!);
      const objectUrl = URL.createObjectURL(blob);
      if (tab) {
        tab.location.href = objectUrl;
      }
      setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
    } catch {
      tab?.close();
      this.toastService.error(
        this.translateService.translate('experiment.documents.error.download')(),
      );
    }
  }

  // GLOBAL errors (403, 500) are already toasted by the restErrorInterceptor
  private async upload(file: File) {
    try {
      await this.documentService.uploadDocument(this.experimentId(), file);
    } catch (err) {
      toErrorDtos(err)
        .filter((error) => error.target !== ErrorDto.TargetEnum.Global)
        .forEach((error) =>
          this.toastService.error(
            this.translateService.translate(
              error.messageKey ?? 'experiment.documents.error.upload',
              error.params,
            )(),
            file.name,
          ),
        );
    }
  }
}
