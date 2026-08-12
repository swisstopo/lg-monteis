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
