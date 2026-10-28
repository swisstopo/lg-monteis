import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, input, resource, signal } from '@angular/core';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { DocumentResponseDto } from '@core/generated';
import { toErrorDtos } from '@core/http/api-error.model';
import { ToastService } from '@core/notifications/toast.service';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { FileDownloadService } from '@ui/file-download/file-download.service';
import { DocumentSource } from './document-source';
import { MAX_DOCUMENT_SIZE_BYTES, MAX_DOCUMENT_SIZE_MB } from './max-document-size';

@Component({
  selector: 'app-documents',
  imports: [DatePipe, MatButton, MatIconButton, MatIcon, MatProgressSpinner, TranslatePipe],
  templateUrl: './documents.html',
  styleUrl: './documents.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Documents {
  private readonly source = inject(DocumentSource);
  private readonly fileDownloadService = inject(FileDownloadService);
  private readonly toastService = inject(ToastService);
  private readonly translateService = inject(TranslateService);

  readonly ownerId = input.required<string>();
  readonly readOnly = input(false);

  protected readonly uploading = signal(false);

  protected readonly documents = resource({
    params: () => this.ownerId(),
    loader: ({ params: ownerId }) => this.source.list(ownerId),
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

  protected download(document: DocumentResponseDto): Promise<void> {
    return this.toastOnFailure(async () =>
      this.fileDownloadService.download(await this.contentOf(document), document.fileName!),
    );
  }

  protected view(document: DocumentResponseDto): Promise<void> {
    return this.toastOnFailure(() =>
      this.fileDownloadService.openInNewTab(() => this.contentOf(document)),
    );
  }

  // checked here as well, so a too large file is not sent in full just to be refused
  private async uploadFile(file: File): Promise<void> {
    if (file.size > MAX_DOCUMENT_SIZE_BYTES) {
      this.toastService.error(
        this.translateService.translate('document.validation.tooLarge', {
          max: `${MAX_DOCUMENT_SIZE_MB} MB`,
        })(),
        file.name,
      );
      return;
    }
    try {
      await this.source.upload(this.ownerId(), file);
    } catch (error) {
      this.toastUploadErrors(error, file.name);
    }
  }

  // a body without message key (network error, 500) still gets the generic toast
  private toastUploadErrors(error: unknown, fileName: string): void {
    const messages = toErrorDtos(error).map((dto) =>
      this.translateService.translate(dto.messageKey ?? 'document.error.upload', dto.params)(),
    );
    if (messages.length === 0) {
      messages.push(this.translateService.translate('document.error.upload')());
    }
    messages.forEach((message) => this.toastService.error(message, fileName));
  }

  private async toastOnFailure(action: () => Promise<void>): Promise<void> {
    try {
      await action();
    } catch {
      this.toastService.error(this.translateService.translate('document.error.download')());
    }
  }

  private contentOf(document: DocumentResponseDto): Promise<Blob> {
    return this.source.content(this.ownerId(), document.id!);
  }
}
