import { Aabb, emptyAabb, expandByPoint } from './aabb';
import { IfcElement } from './ifc-element';
import { buildOctree } from './octree';

function boxAt(x: number, y: number, z: number): Aabb {
  const bounds = emptyAabb();
  expandByPoint(bounds, x - 0.1, y - 0.1, z - 0.1);
  expandByPoint(bounds, x + 0.1, y + 0.1, z + 0.1);
  return bounds;
}

function elementAt(x: number, y: number, z: number): IfcElement {
  return {
    globalId: `${x},${y},${z}`,
    ifcType: 'IfcElement',
    name: '',
    positions: new Float32Array(),
    normals: new Float32Array(),
    indices: new Uint32Array(),
    baseColor: [0.7, 0.7, 0.7, 1],
    bounds: boxAt(x, y, z),
  };
}

describe('buildOctree', () => {
  it('keeps everything in a single leaf when under the element cap', () => {
    const elements = [elementAt(0, 0, 0), elementAt(1, 1, 1)];
    const root = buildOctree(elements, { maxElementsPerTile: 50, maxDepth: 8 });

    expect(root.children).toHaveLength(0);
    expect(root.elementIndices.sort()).toEqual([0, 1]);
  });

  it('splits elements spread across octants without losing or duplicating any', () => {
    // One element in each of the 8 octants around the origin.
    const elements = [
      elementAt(-10, -10, -10),
      elementAt(10, -10, -10),
      elementAt(-10, 10, -10),
      elementAt(10, 10, -10),
      elementAt(-10, -10, 10),
      elementAt(10, -10, 10),
      elementAt(-10, 10, 10),
      elementAt(10, 10, 10),
    ];

    const root = buildOctree(elements, { maxElementsPerTile: 1, maxDepth: 8 });

    expect(root.elementIndices).toHaveLength(0);
    expect(root.children.length).toBeGreaterThan(1);

    const leafIndices = root.children.flatMap((child) => child.elementIndices);
    expect(leafIndices.slice().sort((a, b) => a - b)).toEqual([0, 1, 2, 3, 4, 5, 6, 7]);
  });

  it('never subdivides a whole element across tiles', () => {
    const elements = [elementAt(0, 0, 0), elementAt(1, 1, 1), elementAt(2, 2, 2)];
    const root = buildOctree(elements, { maxElementsPerTile: 1, maxDepth: 8 });

    function collectLeafGroups(node: typeof root): number[][] {
      if (node.children.length === 0) {
        return [node.elementIndices];
      }
      return node.children.flatMap(collectLeafGroups);
    }

    for (const group of collectLeafGroups(root)) {
      expect(group.length).toBeGreaterThan(0);
    }
  });

  it('stops at maxDepth even with more elements than the per-tile cap', () => {
    // All clustered in the same octant relative to any subdivision center, so it would recurse
    // forever without the depth cap.
    const elements = Array.from({ length: 10 }, (_, i) => elementAt(i * 0.001, 0, 0));
    const root = buildOctree(elements, { maxElementsPerTile: 1, maxDepth: 2 });

    function maxDepthOf(node: typeof root, depth: number): number {
      if (node.children.length === 0) {
        return depth;
      }
      return Math.max(...node.children.map((child) => maxDepthOf(child, depth + 1)));
    }

    expect(maxDepthOf(root, 0)).toBeLessThanOrEqual(2);
  });
});
