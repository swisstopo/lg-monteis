/** Axis-aligned bounding box in the (already Y-up converted) tile coordinate space. Mirrors
 * tiling/src/geometry/Aabb.h so the browser-side tiler and the native monteis-tiling binary
 * produce equivalent tilesets. */
export interface Aabb {
  min: [number, number, number];
  max: [number, number, number];
}

export function emptyAabb(): Aabb {
  return {
    min: [Infinity, Infinity, Infinity],
    max: [-Infinity, -Infinity, -Infinity],
  };
}

export function isEmpty(bounds: Aabb): boolean {
  return bounds.min[0] > bounds.max[0];
}

export function expandByPoint(bounds: Aabb, x: number, y: number, z: number): void {
  bounds.min[0] = Math.min(bounds.min[0], x);
  bounds.min[1] = Math.min(bounds.min[1], y);
  bounds.min[2] = Math.min(bounds.min[2], z);
  bounds.max[0] = Math.max(bounds.max[0], x);
  bounds.max[1] = Math.max(bounds.max[1], y);
  bounds.max[2] = Math.max(bounds.max[2], z);
}

export function expandByAabb(bounds: Aabb, other: Aabb): void {
  if (isEmpty(other)) {
    return;
  }
  expandByPoint(bounds, ...other.min);
  expandByPoint(bounds, ...other.max);
}

export function center(bounds: Aabb): [number, number, number] {
  return [
    (bounds.min[0] + bounds.max[0]) * 0.5,
    (bounds.min[1] + bounds.max[1]) * 0.5,
    (bounds.min[2] + bounds.max[2]) * 0.5,
  ];
}

export function halfExtents(bounds: Aabb): [number, number, number] {
  return [
    (bounds.max[0] - bounds.min[0]) * 0.5,
    (bounds.max[1] - bounds.min[1]) * 0.5,
    (bounds.max[2] - bounds.min[2]) * 0.5,
  ];
}

export function diagonalLength(bounds: Aabb): number {
  const dx = bounds.max[0] - bounds.min[0];
  const dy = bounds.max[1] - bounds.min[1];
  const dz = bounds.max[2] - bounds.min[2];
  return Math.sqrt(dx * dx + dy * dy + dz * dz);
}
