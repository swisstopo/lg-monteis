import { Aabb } from './aabb';

/**
 * One triangulated IFC product, already in world coordinates and already converted from IFC's
 * Z-up axes to glTF's Y-up axes, so nothing downstream needs to know about IFC axis conventions.
 * Mirrors tiling/src/geometry/IfcElement.h.
 */
export interface IfcElement {
  globalId: string;
  ifcType: string;
  name: string;

  /** Flat xyz triples, glTF Y-up, one entry per vertex. */
  positions: Float32Array;
  /** Flat xyz triples, same vertex count as positions. Empty if the source geometry had no
   * normals, in which case the glTF primitive omits the NORMAL attribute. */
  normals: Float32Array;
  /** Triangle list. */
  indices: Uint32Array;

  baseColor: [number, number, number, number];

  bounds: Aabb;
}

/**
 * Same shape as {@link IfcElement}, but with full double-precision positions. Used only for the
 * brief window between extraction and {@link recenterElements}: a real project's IFC coordinates
 * routinely carry a large absolute offset (site/project coordinate systems, easily in the
 * hundreds of thousands of units), and truncating that straight to float32 - as {@link IfcElement}
 * ultimately requires - would leave only a handful of significant digits for the model's actual
 * (much smaller) extent. Recentering has to happen while positions are still this precise.
 */
export interface RawIfcElement extends Omit<IfcElement, 'positions'> {
  positions: Float64Array;
}
