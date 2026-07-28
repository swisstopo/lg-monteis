import { HttpErrorResponse } from '@angular/common/http';
import { ErrorDto } from '../../core/generated';

/**
 * Normalizes any error thrown by an API call into a list of {@link ErrorDto}.
 */
export function toErrorDtos(error: unknown): ErrorDto[] {
  const body = error instanceof HttpErrorResponse ? error.error : error;

  if (Array.isArray(body)) {
    return body as ErrorDto[];
  }
  if (body && typeof body === 'object') {
    return [body as ErrorDto];
  }
  return [];
}

/**
 * Simplified error shape for display in the UI (e.g. a toast).
 */
export interface DisplayError {
  title: string;
  message: string;
}

export function toDisplayError(errors: ErrorDto[]): DisplayError[] {
  if (errors.length === 0) {
    return [{ title: 'Error', message: 'An unknown error occurred.' }];
  }

  return errors.map((error: ErrorDto) => {
    return {
      title: error.messageKey ?? 'Error',
      message: error.field ? `${error.target}.${error.field}: ${error.actualValue}` : '',
    };
  });
}

/** Convenience combining {@link toErrorDtos} and {@link toDisplayError}. */
export function toDisplayErrorFromUnknown(error: unknown): DisplayError[] {
  return toDisplayError(toErrorDtos(error));
}
