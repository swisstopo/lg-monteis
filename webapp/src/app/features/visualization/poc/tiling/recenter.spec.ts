import { emptyAabb, expandByPoint } from './aabb';
import { RawIfcElement } from './ifc-element';
import { recenterElements } from './recenter';

function elementWithPositions(positions: number[]): RawIfcElement {
  const bounds = emptyAabb();
  for (let i = 0; i + 2 < positions.length; i += 3) {
    expandByPoint(bounds, positions[i], positions[i + 1], positions[i + 2]);
  }

  return {
    globalId: 'GUID',
    ifcType: 'IfcWall',
    name: '',
    positions: new Float64Array(positions),
    normals: new Float32Array(),
    indices: new Uint32Array(),
    baseColor: [0.7, 0.7, 0.7, 1],
    bounds,
  };
}

describe('recenterElements', () => {
  it('centers the model horizontally and drops it onto y=0', () => {
    // Two elements straddling a large, real-project-style offset.
    const a = elementWithPositions([2_600_000, 100, 1_200_000, 2_600_010, 110, 1_200_000]);
    const b = elementWithPositions([2_600_000, 100, 1_200_010, 2_600_010, 105, 1_200_010]);

    const [ra, rb] = recenterElements([a, b]);

    // Overall bounds before recentering: x in [2_600_000, 2_600_010], y in [100, 110], z in
    // [1_200_000, 1_200_010] -> center x = 2_600_005, center z = 1_200_005, min y = 100.
    expect(ra.positions[0]).toBeCloseTo(2_600_000 - 2_600_005);
    expect(ra.positions[1]).toBeCloseTo(100 - 100);
    expect(ra.positions[2]).toBeCloseTo(1_200_000 - 1_200_005);
    expect(ra.bounds.min[1]).toBeCloseTo(0);
    expect(rb.positions).toBeInstanceOf(Float32Array);
  });

  it('keeps the small-scale precision that would otherwise be lost to float32', () => {
    // 0.001-scale detail riding on a large absolute offset - if the offset were baked into a
    // Float32Array directly (rather than subtracted in double precision first) this detail
    // would be rounded away.
    const a = elementWithPositions([5_000_000, 0, 5_000_000, 5_000_000.001, 0, 5_000_000]);

    const [recentered] = recenterElements([a]);

    expect(recentered.positions[3] - recentered.positions[0]).toBeCloseTo(0.001, 4);
  });

  it('returns an empty array for an empty element list', () => {
    expect(recenterElements([])).toEqual([]);
  });
});
