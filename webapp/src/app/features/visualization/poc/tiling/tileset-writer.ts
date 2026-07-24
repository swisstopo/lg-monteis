import { Aabb, center, diagonalLength, halfExtents } from './aabb';
import { writeGlb } from './gltf-writer';
import { IfcElement } from './ifc-element';
import { OctreeNode } from './octree';

export interface TilesetResult {
  tilesetJson: unknown;
  /** Filename (e.g. "tile_0.glb") -> GLB bytes. */
  tiles: Map<string, Uint8Array>;
}

function boundingVolumeOf(bounds: Aabb): unknown {
  const c = center(bounds);
  const half = halfExtents(bounds);
  // 3D Tiles axis-aligned box: center followed by the three half-axis vectors. Everything here
  // lives in the same Y-up, non-georeferenced space as the tile content, so no tile "transform"
  // matrix is needed on top of this.
  return {
    box: [c[0], c[1], c[2], half[0], 0, 0, 0, half[1], 0, 0, 0, half[2]],
  };
}

function buildTileJson(
  node: OctreeNode,
  elements: IfcElement[],
  tiles: Map<string, Uint8Array>,
  tileCounter: { next: number },
): Record<string, unknown> {
  const isLeaf = node.children.length === 0;
  const tile: Record<string, unknown> = {
    boundingVolume: boundingVolumeOf(node.bounds),
    // Leaves are already full detail, so there's no error incurred by not refining further.
    // Internal nodes report their own extent, so the client only descends into children once
    // this tile would be too coarse to be worth the screen space it occupies.
    geometricError: isLeaf ? 0 : diagonalLength(node.bounds),
    refine: 'ADD',
  };

  if (node.elementIndices.length > 0) {
    const filename = `tile_${tileCounter.next++}.glb`;
    tiles.set(filename, writeGlb(elements, node.elementIndices));
    tile['content'] = { uri: filename };
  }

  if (node.children.length > 0) {
    tile['children'] = node.children.map((child) =>
      buildTileJson(child, elements, tiles, tileCounter),
    );
  }

  return tile;
}

/** Writes the 3D Tiles `tileset.json` describing the octree, plus one `.glb` per leaf that
 * actually holds geometry. Mirrors tiling/src/export/TilesetWriter.{h,cpp}. */
export function buildTileset(root: OctreeNode, elements: IfcElement[]): TilesetResult {
  const tiles = new Map<string, Uint8Array>();
  const rootTile = buildTileJson(root, elements, tiles, { next: 0 });

  const tilesetJson = {
    asset: { version: '1.0', generator: 'monteis-tiling-web' },
    geometricError: rootTile['geometricError'],
    root: rootTile,
  };

  return { tilesetJson, tiles };
}
