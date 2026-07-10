import { ErrorDTO } from '../generated';

export function normalizeErrors(error: any): ErrorDTO[] {
  if (!error) {
    return [createFallbackError()];
  }

  if (Array.isArray(error)) {
    return error as ErrorDTO[];
  }

  if (isErrorDTO(error)) {
    return [error];
  }

  if (typeof error === 'string') {
    try {
      const parsed = JSON.parse(error);
      if (Array.isArray(parsed)) return parsed as ErrorDTO[];
      if (isErrorDTO(parsed)) return [parsed];
    } catch {
      // Ignore parse errors, fallback below
    }
  }

  return [createFallbackError()];
}

function isErrorDTO(value: any): value is ErrorDTO {
  return value && typeof value === 'object' && typeof value.messageKey === 'string';
}

function createFallbackError(): ErrorDTO {
  return {
    field: 'global',
    actualValue: null,
    messageKey: 'error.system.internal',
    params: {},
  };
}
