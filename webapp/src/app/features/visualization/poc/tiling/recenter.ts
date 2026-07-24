import { Aabb, center, emptyAabb, expandByAabb } from './aabb';
import { IfcElement, RawIfcElement } from './ifc-element';

/**
 * Shifts every element so the model's horizontal center sits at x=0/z=0 and its lowest point
 * sits at y=0, and only then truncates positions down to the float32 {@link IfcElement} needs.
 *
 * This matters beyond just centering the view: IFC files authored against a real site/project
 * coordinate system routinely carry absolute coordinates in the hundreds of thousands of units.
 * Truncating those large absolute values straight to float32 would leave only a handful of
 * significant digits for the model's actual (much smaller) extent, which shows up as jittery
 * geometry and a camera/controls target that doesn't line up with what's rendered - so this
 * takes {@link RawIfcElement}s (still full double precision) in, subtracts the offset while
 * still in double precision, and only then produces the final float32 {@link IfcElement}s.
 */
export function recenterElements(elements: RawIfcElement[]): IfcElement[] {
  if (elements.length === 0) {
    return [];
  }

  const overallBounds = emptyAabb();
  for (const element of elements) {
    expandByAabb(overallBounds, element.bounds);
  }

  const [offsetX, , offsetZ] = center(overallBounds);
  const offsetY = overallBounds.min[1];

  return elements.map((element) => {
    const positions = new Float32Array(element.positions.length);
    for (let i = 0; i + 2 < positions.length; i += 3) {
      positions[i] = element.positions[i] - offsetX;
      positions[i + 1] = element.positions[i + 1] - offsetY;
      positions[i + 2] = element.positions[i + 2] - offsetZ;
    }

    return {
      ...element,
      positions,
      bounds: shiftBounds(element.bounds, offsetX, offsetY, offsetZ),
    };
  });
}

function shiftBounds(bounds: Aabb, offsetX: number, offsetY: number, offsetZ: number): Aabb {
  return {
    min: [bounds.min[0] - offsetX, bounds.min[1] - offsetY, bounds.min[2] - offsetZ],
    max: [bounds.max[0] - offsetX, bounds.max[1] - offsetY, bounds.max[2] - offsetZ],
  };
}
