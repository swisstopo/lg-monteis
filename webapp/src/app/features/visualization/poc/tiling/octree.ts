import { Aabb, center, emptyAabb, expandByAabb } from './aabb';
import { IfcElement } from './ifc-element';

export interface OctreeConfig {
  /** A node stops subdividing once it holds this many or fewer elements. */
  maxElementsPerTile: number;
  /** Hard depth cap, independent of element count, so a handful of huge elements spread across
   * the whole model can't recurse forever. */
  maxDepth: number;
}

/** One node of the spatial partition that becomes one 3D Tiles tile. Elements live only at
 * leaves - internal nodes exist purely to group children under a coarser bounding volume for
 * frustum/SSE culling, per the 3D Tiles ADD refinement model (children add detail, they don't
 * replace a simplified parent). Mirrors tiling/src/tiling/Octree.{h,cpp}. */
export interface OctreeNode {
  bounds: Aabb;
  elementIndices: number[];
  children: OctreeNode[];
}

function boundsOf(elements: IfcElement[], indices: number[]): Aabb {
  const bounds = emptyAabb();
  for (const index of indices) {
    expandByAabb(bounds, elements[index].bounds);
  }
  return bounds;
}

function octantOf(centerPoint: [number, number, number], elementBounds: Aabb): number {
  const elementCenter = center(elementBounds);
  let octant = 0;
  if (elementCenter[0] >= centerPoint[0]) octant |= 1;
  if (elementCenter[1] >= centerPoint[1]) octant |= 2;
  if (elementCenter[2] >= centerPoint[2]) octant |= 4;
  return octant;
}

function buildNode(
  elements: IfcElement[],
  indices: number[],
  depth: number,
  config: OctreeConfig,
): OctreeNode {
  const bounds = boundsOf(elements, indices);

  if (indices.length <= config.maxElementsPerTile || depth >= config.maxDepth) {
    return { bounds, elementIndices: indices, children: [] };
  }

  const centerPoint = center(bounds);
  const buckets: number[][] = Array.from({ length: 8 }, () => []);
  for (const index of indices) {
    buckets[octantOf(centerPoint, elements[index].bounds)].push(index);
  }

  const nonEmptyBuckets = buckets.filter((bucket) => bucket.length > 0);
  if (nonEmptyBuckets.length <= 1) {
    // All centroids landed in the same octant (e.g. a handful of huge elements clustered
    // together) - subdividing further wouldn't separate anything, so stop here rather than
    // recursing forever at the same size.
    return { bounds, elementIndices: indices, children: [] };
  }

  const children = nonEmptyBuckets.map((bucket) => buildNode(elements, bucket, depth + 1, config));
  return { bounds, elementIndices: [], children };
}

/** Builds a spatial partition over `elements` by recursively splitting on the bounding-box
 * center and bucketing each element by its centroid's octant. Whole elements are never split
 * across tiles. */
export function buildOctree(elements: IfcElement[], config: OctreeConfig): OctreeNode {
  const allIndices = elements.map((_, index) => index);
  return buildNode(elements, allIndices, 0, config);
}
