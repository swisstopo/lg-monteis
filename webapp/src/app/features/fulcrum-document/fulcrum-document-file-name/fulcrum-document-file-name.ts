import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { FulcrumDocumentResponseDto } from '@core/generated';
import { TranslatePipe } from '@ngx-translate/core';
import { ICellRendererAngularComp } from 'ag-grid-angular';
import { ICellRendererParams } from 'ag-grid-community';

@Component({
  selector: 'app-fulcrum-document-file-name',
  templateUrl: './fulcrum-document-file-name.html',
  styleUrl: './fulcrum-document-file-name.scss',
  imports: [MatIcon, MatTooltip, TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FulcrumDocumentFileName implements ICellRendererAngularComp {
  protected readonly document = signal<FulcrumDocumentResponseDto | undefined>(undefined);

  agInit(params: ICellRendererParams<FulcrumDocumentResponseDto>): void {
    this.refresh(params);
  }

  refresh(params: ICellRendererParams<FulcrumDocumentResponseDto>): boolean {
    this.document.set(params.data);
    return true;
  }
}
