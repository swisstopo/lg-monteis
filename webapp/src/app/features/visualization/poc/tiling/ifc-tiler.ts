import { IfcAPI } from 'web-ifc';
import { applyDebugElementFilter } from './debug-element-filter';
import { extractElements } from './ifc-extractor';
import { buildOctree, OctreeConfig } from './octree';
import { recenterElements } from './recenter';
import { buildTileset, TilesetResult } from './tileset-writer';

export type TilingProgressPhase = 'parsing' | 'triangulating' | 'partitioning' | 'writing';

export interface TilingOutcome extends TilesetResult {
  /** Number of IFC elements web-ifc could not triangulate (unsupported BREP/mesh types, or
   * degenerate geometry) and that were therefore left out of the tileset. */
  skippedCount: number;
}

/**
 * Tiles a raw IFC file entirely client-side: parses and triangulates it with web-ifc (WASM),
 * partitions the result into an octree, and writes a 3D Tiles tileset - the in-browser
 * equivalent of the whole monteis-tiling native pipeline (see /tiling/README.md), so it can run
 * inside a Web Worker on upload instead of needing a server-side IfcOpenShell build.
 */
export async function tileIfcFile(
  ifcBytes: Uint8Array,
  config: OctreeConfig,
  onProgress?: (phase: TilingProgressPhase) => void,
): Promise<TilingOutcome> {
  const api = new IfcAPI();
  api.SetWasmPath('/wasm/', true);
  // Only the single-threaded wasm binary is bundled (public/wasm/web-ifc.wasm), so force
  // single-threaded mode rather than letting web-ifc probe for a nonexistent -mt variant.
  await api.Init(undefined, true);

  onProgress?.('parsing');
  const modelID = api.OpenModel(ifcBytes);
  if (modelID < 0) {
    throw new Error('Failed to parse IFC file');
  }

  try {
    onProgress?.('triangulating');
    const { elements: allElements, skippedExpressIds } = extractElements(api, modelID);
    if (allElements.length === 0) {
      throw new Error('No triangulated elements found in IFC file');
    }
    if (skippedExpressIds.length > 0) {
      console.warn(
        `web-ifc could not triangulate ${skippedExpressIds.length} element(s), skipping them:`,
        skippedExpressIds,
      );
    }

    const filtered = applyDebugElementFilter(allElements);
    const elements = recenterElements(filtered);

    onProgress?.('partitioning');
    const root = buildOctree(elements, config);

    onProgress?.('writing');
    return { ...buildTileset(root, elements), skippedCount: skippedExpressIds.length };
  } finally {
    api.CloseModel(modelID);
  }
}
