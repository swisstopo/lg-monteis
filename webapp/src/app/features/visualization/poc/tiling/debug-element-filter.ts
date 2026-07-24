/**
 * TEMPORARY dev knob: while iterating on the viewer, tile only elements whose IFC Name contains
 * this substring instead of the whole (potentially very large) uploaded model - e.g. one
 * borehole's worth of elements instead of an entire site. Set to `null` to tile everything.
 *
 * The sample scene shipped in /tiling names its elements like "BCL-10 - 1 - shotcrete", so
 * "BCL-10" is a reasonable starting guess, but every project's naming will differ - if this
 * matches nothing, the console lists real element names from the file you uploaded so you can
 * pick one.
 */
export const DEBUG_ELEMENT_NAME_FILTER: string | null = null;

/**
 * Applies {@link DEBUG_ELEMENT_NAME_FILTER}, throwing with a sample of actual element names if
 * it matches nothing (rather than silently producing an empty tileset). Generic over the element
 * type since this runs before recentering, on the still-double-precision {@link RawIfcElement}s.
 */
export function applyDebugElementFilter<T extends { name: string }>(elements: T[]): T[] {
  if (DEBUG_ELEMENT_NAME_FILTER === null) {
    return elements;
  }

  const filtered = elements.filter((element) => element.name.includes(DEBUG_ELEMENT_NAME_FILTER));
  if (filtered.length === 0) {
    const sampleNames = [...new Set(elements.map((element) => element.name))].slice(0, 20);
    console.warn(
      `DEBUG_ELEMENT_NAME_FILTER "${DEBUG_ELEMENT_NAME_FILTER}" matched no elements. Sample of` +
        ' element names actually present in this file (edit debug-element-filter.ts to match' +
        ' one of these):',
      sampleNames,
    );
    throw new Error(
      `DEBUG_ELEMENT_NAME_FILTER "${DEBUG_ELEMENT_NAME_FILTER}" matched no elements - see the` +
        ' browser console for a sample of real element names in this file.',
    );
  }

  console.info(
    `DEBUG_ELEMENT_NAME_FILTER "${DEBUG_ELEMENT_NAME_FILTER}" kept ${filtered.length} of` +
      ` ${elements.length} elements.`,
  );
  return filtered;
}
