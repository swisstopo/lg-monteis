import type { FlatMesh, IfcAPI } from 'web-ifc';
import { emptyAabb, expandByPoint, isEmpty } from './aabb';
import { RawIfcElement } from './ifc-element';

/**
 * IFC is Z-up, right-handed. glTF (and therefore our tiles and the tileset's bounding boxes) is
 * Y-up, right-handed. This is the same fixed rotation IfcConvert's own glTF serializer applies,
 * kept explicit here to match tiling/src/geometry/IfcGeometryExtractor.cpp exactly.
 */
function ifcToGltfUpAxis(x: number, y: number, z: number): [number, number, number] {
  return [x, z, -y];
}

/** web-ifc returns vertex data interleaved as [x, y, z, nx, ny, nz] per vertex, in the
 * geometry's own local space; `matrix` is the placement (column-major, matching three.js'
 * Matrix4.elements) that bakes it into world space - the same "use world coords" behavior the
 * native tiler gets from IfcOpenShell's UseWorldCoords setting. */
function transformAndConvert(
  interleaved: Float32Array,
  matrix: number[],
): { positions: Float64Array; normals: Float32Array } {
  const vertexCount = interleaved.length / 6;
  // Float64: this is world-space, and a real project's IFC coordinates routinely carry a large
  // absolute offset (site/project coordinate system) - truncating that to float32 here, before
  // recenterElements has a chance to subtract it out, would throw away the model's actual
  // (much smaller) detail. Normals are unit directions, not positions, so they don't have this
  // problem and can stay float32.
  const positions = new Float64Array(vertexCount * 3);
  const normals = new Float32Array(vertexCount * 3);

  for (let i = 0; i < vertexCount; i++) {
    const base = i * 6;
    const px = interleaved[base];
    const py = interleaved[base + 1];
    const pz = interleaved[base + 2];
    const nx = interleaved[base + 3];
    const ny = interleaved[base + 4];
    const nz = interleaved[base + 5];

    const wx = matrix[0] * px + matrix[4] * py + matrix[8] * pz + matrix[12];
    const wy = matrix[1] * px + matrix[5] * py + matrix[9] * pz + matrix[13];
    const wz = matrix[2] * px + matrix[6] * py + matrix[10] * pz + matrix[14];

    let dx = matrix[0] * nx + matrix[4] * ny + matrix[8] * nz;
    let dy = matrix[1] * nx + matrix[5] * ny + matrix[9] * nz;
    let dz = matrix[2] * nx + matrix[6] * ny + matrix[10] * nz;
    const length = Math.hypot(dx, dy, dz) || 1;
    dx /= length;
    dy /= length;
    dz /= length;

    const [px2, py2, pz2] = ifcToGltfUpAxis(wx, wy, wz);
    const [nx2, ny2, nz2] = ifcToGltfUpAxis(dx, dy, dz);

    const out = i * 3;
    positions[out] = px2;
    positions[out + 1] = py2;
    positions[out + 2] = pz2;
    normals[out] = nx2;
    normals[out + 1] = ny2;
    normals[out + 2] = nz2;
  }

  return { positions, normals };
}

function concatFloat32(chunks: Float32Array[]): Float32Array {
  const total = chunks.reduce((sum, chunk) => sum + chunk.length, 0);
  const out = new Float32Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    out.set(chunk, offset);
    offset += chunk.length;
  }
  return out;
}

function concatFloat64(chunks: Float64Array[]): Float64Array {
  const total = chunks.reduce((sum, chunk) => sum + chunk.length, 0);
  const out = new Float64Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    out.set(chunk, offset);
    offset += chunk.length;
  }
  return out;
}

function concatUint32(chunks: Uint32Array[]): Uint32Array {
  const total = chunks.reduce((sum, chunk) => sum + chunk.length, 0);
  const out = new Uint32Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    out.set(chunk, offset);
    offset += chunk.length;
  }
  return out;
}

/** Raw IFC line attributes come back wrapped as `{ value, type, name }` for simple attributes -
 * this unwraps that regardless of whether the attribute is present. */
function stringAttribute(attribute: unknown): string {
  if (attribute && typeof attribute === 'object' && 'value' in attribute) {
    const value = (attribute as { value: unknown }).value;
    return value === null || value === undefined ? '' : String(value);
  }
  return '';
}

function extractOneMesh(api: IfcAPI, modelID: number, mesh: FlatMesh): RawIfcElement | null {
  const geometryCount = mesh.geometries.size();
  if (geometryCount === 0) {
    return null;
  }

  const positionChunks: Float64Array[] = [];
  const normalChunks: Float32Array[] = [];
  const indexChunks: Uint32Array[] = [];
  let baseColor: [number, number, number, number] = [0.7, 0.7, 0.7, 1];
  let vertexOffset = 0;

  for (let i = 0; i < geometryCount; i++) {
    const placedGeometry = mesh.geometries.get(i);
    const geometry = api.GetGeometry(modelID, placedGeometry.geometryExpressID);
    const vertexDataSize = geometry.GetVertexDataSize();
    const indexDataSize = geometry.GetIndexDataSize();
    if (vertexDataSize === 0 || indexDataSize === 0) {
      continue;
    }

    const interleaved = api.GetVertexArray(geometry.GetVertexData(), vertexDataSize);
    const rawIndices = api.GetIndexArray(geometry.GetIndexData(), indexDataSize);
    const { positions, normals } = transformAndConvert(
      interleaved,
      placedGeometry.flatTransformation,
    );

    const offsetIndices = new Uint32Array(rawIndices.length);
    for (let j = 0; j < rawIndices.length; j++) {
      offsetIndices[j] = rawIndices[j] + vertexOffset;
    }

    if (i === 0) {
      const { color } = placedGeometry;
      baseColor = [color.x, color.y, color.z, color.w];
    }

    positionChunks.push(positions);
    normalChunks.push(normals);
    indexChunks.push(offsetIndices);
    vertexOffset += positions.length / 3;
  }

  if (positionChunks.length === 0) {
    return null;
  }

  const positions = concatFloat64(positionChunks);
  const normals = concatFloat32(normalChunks);
  const indices = concatUint32(indexChunks);

  const bounds = emptyAabb();
  for (let i = 0; i + 2 < positions.length; i += 3) {
    expandByPoint(bounds, positions[i], positions[i + 1], positions[i + 2]);
  }
  if (isEmpty(bounds)) {
    return null;
  }

  const line = api.GetLine(modelID, mesh.expressID) as
    { GlobalId?: unknown; Name?: unknown; constructor: { name: string } } | undefined;

  return {
    globalId: stringAttribute(line?.GlobalId),
    ifcType: line?.constructor.name ?? 'IfcElement',
    name: stringAttribute(line?.Name),
    positions,
    normals,
    indices,
    baseColor,
    bounds,
  };
}

export interface ExtractionResult {
  elements: RawIfcElement[];
  /** Express IDs web-ifc could not produce triangulated geometry for - either because its BREP
   * engine logged an "unexpected mesh type"/"no basis found" warning internally and returned no
   * geometry, or because extracting this one mesh threw outright. Either way, the rest of the
   * model still extracts fine, so these are just dropped rather than failing the whole tiling
   * run. */
  skippedExpressIds: number[];
}

/** Parses every product's geometry out of an already-open web-ifc model, triangulated and
 * already in world-space, Y-up coordinates - the browser-side equivalent of
 * tiling/src/geometry/IfcGeometryExtractor.cpp. Positions are still full double precision at
 * this point - see {@link RawIfcElement} and recenter.ts. */
export function extractElements(api: IfcAPI, modelID: number): ExtractionResult {
  const elements: RawIfcElement[] = [];
  const skippedExpressIds: number[] = [];

  api.StreamAllMeshes(modelID, (mesh) => {
    let element: RawIfcElement | null = null;
    try {
      element = extractOneMesh(api, modelID, mesh);
    } catch (error) {
      console.warn(`Skipping IFC element ${mesh.expressID}: could not be triangulated`, error);
    }

    if (element) {
      elements.push(element);
    } else {
      skippedExpressIds.push(mesh.expressID);
    }
  });

  return { elements, skippedExpressIds };
}
