import { HttpClient, HttpResponse } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { skipGlobalErrorToast } from '../../../core/http/http-context';

export const TILESET_URL_PREFIX = `${window.location.origin}/api/tilesets`;

/**
 * Loads the Giro3d tileset and its tiles through `HttpClient`, so that the interceptors
 * (authorization above all) apply to them like to any other request.
 */
@Service()
export class MeasurementsTiles {
  private readonly httpClient = inject(HttpClient);

  async fetch(url: string, options?: { signal?: AbortSignal | null }): Promise<Response> {
    if (!url.startsWith(TILESET_URL_PREFIX)) {
      throw new Error(`Invalid tileset or tile URL, must begin with '${TILESET_URL_PREFIX}'`);
    }
    return toResponse(await this.request(url, options?.signal));
  }

  /**
   * `firstValueFrom` cannot cancel a request, so subscribe manually and unsubscribe on abort –
   * otherwise tiles the renderer has already discarded keep downloading.
   */
  private request(url: string, signal?: AbortSignal | null): Promise<HttpResponse<ArrayBuffer>> {
    return new Promise((resolve, reject) => {
      if (signal?.aborted) {
        reject(abortError(signal));
        return;
      }

      // To implement request cancellation via the AbortSignal: `firstValueFrom`
      // cannot cancel a request, so subscribe manually and unsubscribe when the
      // signal emits.
      const subscription = this.httpClient
        .get(url, {
          observe: 'response',
          responseType: 'arraybuffer',
          context: skipGlobalErrorToast(),
        })
        .subscribe({ next: resolve, error: reject });

      if (!signal) return;

      const onAbort = () => {
        subscription.unsubscribe();
        reject(abortError(signal));
      };
      signal.addEventListener('abort', onAbort, { once: true });
      subscription.add(() => signal.removeEventListener('abort', onAbort));
    });
  }
}

function toResponse(response: HttpResponse<ArrayBuffer>): Response {
  const headers = new Headers();
  response.headers
    .keys()
    .forEach((name) => headers.set(name, response.headers.getAll(name)!.join(', ')));

  return new Response(response.body, { status: response.status, headers });
}

function abortError(signal: AbortSignal): unknown {
  // Abort with DOMException, since that is what `fetch` does as well
  return signal.reason ?? new DOMException('The operation was aborted.', 'AbortError');
}
