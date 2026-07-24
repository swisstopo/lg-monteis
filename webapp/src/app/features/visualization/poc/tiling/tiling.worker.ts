import { tileIfcFile, TilingProgressPhase } from './ifc-tiler';
import { OctreeConfig } from './octree';

export interface TilingWorkerRequest {
  ifcBytes: ArrayBuffer;
  config: OctreeConfig;
}

export type TilingWorkerResponse =
  | { type: 'progress'; phase: TilingProgressPhase }
  | { type: 'done'; files: { name: string; data: ArrayBuffer }[]; skippedCount: number }
  | { type: 'error'; message: string };

// Typed through the `Worker` interface (the shape seen from the main thread) rather than the
// "webworker" lib's `DedicatedWorkerGlobalScope`, since both expose the same postMessage/
// addEventListener surface and this lets the file type-check under the app's normal "dom" lib
// without a second, worker-only tsconfig.
const workerScope = self as unknown as Worker;

workerScope.addEventListener('message', (event: MessageEvent<TilingWorkerRequest>) => {
  void handleMessage(event.data);
});

async function handleMessage(request: TilingWorkerRequest): Promise<void> {
  try {
    const result = await tileIfcFile(new Uint8Array(request.ifcBytes), request.config, (phase) => {
      workerScope.postMessage({ type: 'progress', phase } satisfies TilingWorkerResponse);
    });

    const tilesetBytes = new TextEncoder().encode(JSON.stringify(result.tilesetJson, null, 2));
    const files: { name: string; data: ArrayBuffer }[] = [
      { name: 'tileset.json', data: tilesetBytes.buffer as ArrayBuffer },
    ];
    for (const [name, bytes] of result.tiles) {
      files.push({ name, data: bytes.buffer as ArrayBuffer });
    }

    workerScope.postMessage(
      { type: 'done', files, skippedCount: result.skippedCount } satisfies TilingWorkerResponse,
      files.map((file) => file.data),
    );
  } catch (error) {
    workerScope.postMessage({
      type: 'error',
      message: error instanceof Error ? error.message : String(error),
    } satisfies TilingWorkerResponse);
  }
}
