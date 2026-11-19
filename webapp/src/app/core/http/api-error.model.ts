import { HttpErrorResponse } from '@angular/common/http';
import { ErrorDto } from '@core/generated';

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
 * {@link toErrorDtos} for a request made with responseType 'blob' (a file download), whose error
 * body arrives as a blob too.
 */
export async function toErrorDtosFromBlob(error: unknown): Promise<ErrorDto[]> {
  if (!(error instanceof HttpErrorResponse) || !(error.error instanceof Blob)) {
    return toErrorDtos(error);
  }
  try {
    return toErrorDtos(JSON.parse(await error.error.text()));
  } catch {
    return [];
  }
}

export class AppErrorResponse {
  constructor(private readonly error: HttpErrorResponse) {}

  dtosTargetGlobalOrUndefined() {
    return toErrorDtos(this.error).filter(
      (err) => err.target === ErrorDto.TargetEnum.Global || err.target === undefined,
    );
  }

  get status() {
    return this.error.status;
  }

  isUnauthorized() {
    return this.error.status === 401;
  }

  isForbidden() {
    return this.error.status === 403;
  }

  isNotFound() {
    return this.error.status === 404;
  }

  isServerError() {
    // Network failures or backend crashes that never reach our ErrorDto
    // contract (e.g. proxy/HTML error pages) still need to surface to the user.
    return this.error.status === 0 || this.error.status >= 500;
  }
}
