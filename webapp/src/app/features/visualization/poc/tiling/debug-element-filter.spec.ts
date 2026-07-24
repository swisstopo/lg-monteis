import { emptyAabb } from './aabb';
import { applyDebugElementFilter, DEBUG_ELEMENT_NAME_FILTER } from './debug-element-filter';
import { IfcElement } from './ifc-element';

function elementNamed(name: string): IfcElement {
  return {
    globalId: 'GUID',
    ifcType: 'IfcWall',
    name,
    positions: new Float32Array(),
    normals: new Float32Array(),
    indices: new Uint32Array(),
    baseColor: [0.7, 0.7, 0.7, 1],
    bounds: emptyAabb(),
  };
}

describe('applyDebugElementFilter', () => {
  it('keeps only elements whose name contains the configured filter', () => {
    expect(DEBUG_ELEMENT_NAME_FILTER).not.toBeNull();
    const matching = elementNamed(`${DEBUG_ELEMENT_NAME_FILTER} - 1 - rock`);
    const other = elementNamed('unrelated element');

    const result = applyDebugElementFilter([matching, other]);

    expect(result).toEqual([matching]);
  });

  it('throws a descriptive error when nothing matches', () => {
    expect(() => applyDebugElementFilter([elementNamed('unrelated element')])).toThrow(
      /matched no elements/,
    );
  });
});
