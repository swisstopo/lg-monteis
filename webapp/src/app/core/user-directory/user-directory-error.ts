import { toErrorDtos } from '@core/http/api-error.model';

// denied and unavailable ask for different things, a misconfigured Keycloak needs an admin
export function userDirectoryErrorKey(error: unknown): string | undefined {
  if (!error) return undefined;
  return toErrorDtos(error)[0]?.messageKey ?? 'error.user-directory.unavailable';
}
