import { emptyAabb, expandByPoint } from './aabb';
import { writeGlb } from './gltf-writer';
import { IfcElement } from './ifc-element';

function triangleElement(overrides: Partial<IfcElement> = {}): IfcElement {
  const positions = new Float32Array([0, 0, 0, 1, 0, 0, 0, 1, 0]);
  const bounds = emptyAabb();
  for (let i = 0; i + 2 < positions.length; i += 3) {
    expandByPoint(bounds, positions[i], positions[i + 1], positions[i + 2]);
  }

  return {
    globalId: 'GUID-1',
    ifcType: 'IfcWall',
    name: 'Test wall',
    positions,
    normals: new Float32Array([0, 0, 1, 0, 0, 1, 0, 0, 1]),
    indices: new Uint32Array([0, 1, 2]),
    baseColor: [0.5, 0.6, 0.7, 1],
    bounds,
    ...overrides,
  };
}

interface ParsedGlb {
  gltf: {
    asset: { version: string };
    buffers: { byteLength: number }[];
    bufferViews: { byteOffset: number; byteLength: number }[];
    meshes: { primitives: unknown[] }[];
  };
  binByteLength: number;
}

function parseGlb(glb: Uint8Array): ParsedGlb {
  const view = new DataView(glb.buffer, glb.byteOffset, glb.byteLength);
  expect(view.getUint32(0, true)).toBe(0x46546c67); // "glTF"
  expect(view.getUint32(4, true)).toBe(2);
  expect(view.getUint32(8, true)).toBe(glb.byteLength);

  const jsonLength = view.getUint32(12, true);
  expect(view.getUint32(16, true)).toBe(0x4e4f534a); // "JSON"
  const jsonBytes = glb.slice(20, 20 + jsonLength);
  const gltf = JSON.parse(new TextDecoder().decode(jsonBytes));

  const binOffset = 20 + jsonLength;
  const binLength = view.getUint32(binOffset, true);
  expect(view.getUint32(binOffset + 4, true)).toBe(0x004e4942); // "BIN\0"

  return { gltf, binByteLength: binLength };
}

describe('writeGlb', () => {
  it('writes a structurally valid GLB for a single-triangle element', () => {
    const element = triangleElement();
    const glb = writeGlb([element], [0]);

    const { gltf, binByteLength } = parseGlb(glb);
    expect(gltf.asset.version).toBe('2.0');
    expect(gltf.meshes[0].primitives).toHaveLength(1);
    expect(gltf.buffers[0].byteLength).toBeLessThanOrEqual(binByteLength);

    for (const bufferView of gltf.bufferViews) {
      expect(bufferView.byteOffset + bufferView.byteLength).toBeLessThanOrEqual(
        gltf.buffers[0].byteLength,
      );
    }
  });

  it('omits the NORMAL attribute when normals are absent', () => {
    const element = triangleElement({ normals: new Float32Array() });
    const glb = writeGlb([element], [0]);

    const { gltf } = parseGlb(glb) as unknown as {
      gltf: { meshes: { primitives: { attributes: Record<string, number> }[] }[] };
    };
    const attributes = gltf.meshes[0].primitives[0].attributes;
    expect(attributes['POSITION']).toBeDefined();
    expect(attributes['NORMAL']).toBeUndefined();
  });

  it('refuses to write an empty tile', () => {
    const emptyElement = triangleElement({
      positions: new Float32Array(),
      indices: new Uint32Array(),
    });
    expect(() => writeGlb([emptyElement], [0])).toThrow();
  });

  it('packs multiple elements into separate primitives with their own material', () => {
    const a = triangleElement({ globalId: 'A', baseColor: [1, 0, 0, 1] });
    const b = triangleElement({ globalId: 'B', baseColor: [0, 1, 0, 1] });
    const glb = writeGlb([a, b], [0, 1]);

    const { gltf } = parseGlb(glb) as unknown as {
      gltf: { meshes: { primitives: unknown[] }[]; materials: { name: string }[] };
    };
    expect(gltf.meshes[0].primitives).toHaveLength(2);
    expect(gltf.materials.map((m) => m.name)).toEqual(['A', 'B']);
  });
});
