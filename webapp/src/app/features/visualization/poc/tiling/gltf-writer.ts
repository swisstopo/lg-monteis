import { IfcElement } from './ifc-element';

const GLB_MAGIC = 0x46546c67; // "glTF"
const GLB_VERSION = 2;
const CHUNK_TYPE_JSON = 0x4e4f534a; // "JSON"
const CHUNK_TYPE_BIN = 0x004e4942; // "BIN\0"
const COMPONENT_TYPE_FLOAT = 5126;
const COMPONENT_TYPE_UNSIGNED_INT = 5125;
const TARGET_ARRAY_BUFFER = 34962;
const TARGET_ELEMENT_ARRAY_BUFFER = 34963;

/** Accumulates the single binary blob embedded in the GLB's BIN chunk, tracking 4-byte-aligned
 * offsets for each attribute so the JSON chunk's bufferViews can reference them. */
class BinaryBuilder {
  private readonly chunks: Uint8Array[] = [];
  private byteLength = 0;

  get length(): number {
    return this.byteLength;
  }

  appendFloats(data: Float32Array): number {
    return this.append(new Uint8Array(data.buffer, data.byteOffset, data.byteLength));
  }

  appendIndices(data: Uint32Array): number {
    return this.append(new Uint8Array(data.buffer, data.byteOffset, data.byteLength));
  }

  build(): Uint8Array {
    const out = new Uint8Array(this.byteLength);
    let offset = 0;
    for (const chunk of this.chunks) {
      out.set(chunk, offset);
      offset += chunk.length;
    }
    return out;
  }

  private append(bytes: Uint8Array): number {
    this.align();
    const offset = this.byteLength;
    this.chunks.push(bytes.slice());
    this.byteLength += bytes.byteLength;
    return offset;
  }

  private align(): void {
    const padding = (4 - (this.byteLength % 4)) % 4;
    if (padding > 0) {
      this.chunks.push(new Uint8Array(padding));
      this.byteLength += padding;
    }
  }
}

function minOf(positions: Float32Array): [number, number, number] {
  const result: [number, number, number] = [Infinity, Infinity, Infinity];
  for (let i = 0; i + 2 < positions.length; i += 3) {
    result[0] = Math.min(result[0], positions[i]);
    result[1] = Math.min(result[1], positions[i + 1]);
    result[2] = Math.min(result[2], positions[i + 2]);
  }
  return result;
}

function maxOf(positions: Float32Array): [number, number, number] {
  const result: [number, number, number] = [-Infinity, -Infinity, -Infinity];
  for (let i = 0; i + 2 < positions.length; i += 3) {
    result[0] = Math.max(result[0], positions[i]);
    result[1] = Math.max(result[1], positions[i + 1]);
    result[2] = Math.max(result[2], positions[i + 2]);
  }
  return result;
}

function padTo4(bytes: Uint8Array, paddingByte: number): Uint8Array {
  const padding = (4 - (bytes.length % 4)) % 4;
  if (padding === 0) {
    return bytes;
  }
  const out = new Uint8Array(bytes.length + padding);
  out.set(bytes);
  out.fill(paddingByte, bytes.length);
  return out;
}

function writeUint32LE(view: DataView, offset: number, value: number): void {
  view.setUint32(offset, value, true);
}

/** Writes one binary glTF (.glb) tile containing a set of elements, one mesh primitive per
 * element so each keeps its own material color. Mirrors tiling/src/export/GltfWriter.{h,cpp}. */
export function writeGlb(elements: IfcElement[], elementIndices: number[]): Uint8Array {
  const binary = new BinaryBuilder();
  const bufferViews: Record<string, unknown>[] = [];
  const accessors: Record<string, unknown>[] = [];
  const materials: Record<string, unknown>[] = [];
  const primitives: Record<string, unknown>[] = [];

  for (const elementIndex of elementIndices) {
    const element = elements[elementIndex];
    if (element.positions.length === 0 || element.indices.length === 0) {
      continue;
    }

    const vertexCount = element.positions.length / 3;

    const positionsOffset = binary.appendFloats(element.positions);
    bufferViews.push({
      buffer: 0,
      byteOffset: positionsOffset,
      byteLength: element.positions.byteLength,
      target: TARGET_ARRAY_BUFFER,
    });
    const positionsAccessor = accessors.length;
    accessors.push({
      bufferView: bufferViews.length - 1,
      componentType: COMPONENT_TYPE_FLOAT,
      count: vertexCount,
      type: 'VEC3',
      min: minOf(element.positions),
      max: maxOf(element.positions),
    });

    let normalsAccessor = -1;
    if (element.normals.length === element.positions.length) {
      const normalsOffset = binary.appendFloats(element.normals);
      bufferViews.push({
        buffer: 0,
        byteOffset: normalsOffset,
        byteLength: element.normals.byteLength,
        target: TARGET_ARRAY_BUFFER,
      });
      normalsAccessor = accessors.length;
      accessors.push({
        bufferView: bufferViews.length - 1,
        componentType: COMPONENT_TYPE_FLOAT,
        count: vertexCount,
        type: 'VEC3',
      });
    }

    const indicesOffset = binary.appendIndices(element.indices);
    bufferViews.push({
      buffer: 0,
      byteOffset: indicesOffset,
      byteLength: element.indices.byteLength,
      target: TARGET_ELEMENT_ARRAY_BUFFER,
    });
    const indicesAccessor = accessors.length;
    accessors.push({
      bufferView: bufferViews.length - 1,
      componentType: COMPONENT_TYPE_UNSIGNED_INT,
      count: element.indices.length,
      type: 'SCALAR',
    });

    const materialIndex = materials.length;
    materials.push({
      pbrMetallicRoughness: {
        baseColorFactor: element.baseColor,
        metallicFactor: 0,
        roughnessFactor: 1,
      },
      name: element.globalId,
    });

    const attributes: Record<string, number> = { POSITION: positionsAccessor };
    if (normalsAccessor >= 0) {
      attributes['NORMAL'] = normalsAccessor;
    }
    primitives.push({ attributes, indices: indicesAccessor, material: materialIndex });
  }

  if (primitives.length === 0) {
    throw new Error('Refusing to write an empty tile');
  }

  const gltf = {
    asset: { version: '2.0', generator: 'monteis-tiling-web' },
    buffers: [{ byteLength: binary.length }],
    bufferViews,
    accessors,
    materials,
    meshes: [{ primitives }],
    nodes: [{ mesh: 0 }],
    scenes: [{ nodes: [0] }],
    scene: 0,
  };

  const jsonBytes = padTo4(new TextEncoder().encode(JSON.stringify(gltf)), 0x20);
  const binBytes = padTo4(binary.build(), 0x00);

  const totalLength = 12 + 8 + jsonBytes.length + 8 + binBytes.length;
  const out = new Uint8Array(totalLength);
  const view = new DataView(out.buffer);

  writeUint32LE(view, 0, GLB_MAGIC);
  writeUint32LE(view, 4, GLB_VERSION);
  writeUint32LE(view, 8, totalLength);

  let offset = 12;
  writeUint32LE(view, offset, jsonBytes.length);
  writeUint32LE(view, offset + 4, CHUNK_TYPE_JSON);
  out.set(jsonBytes, offset + 8);
  offset += 8 + jsonBytes.length;

  writeUint32LE(view, offset, binBytes.length);
  writeUint32LE(view, offset + 4, CHUNK_TYPE_BIN);
  out.set(binBytes, offset + 8);

  return out;
}
