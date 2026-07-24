import {
  center,
  diagonalLength,
  emptyAabb,
  expandByAabb,
  expandByPoint,
  halfExtents,
  isEmpty,
} from './aabb';

describe('aabb', () => {
  it('is empty until a point is added', () => {
    const bounds = emptyAabb();
    expect(isEmpty(bounds)).toBe(true);

    expandByPoint(bounds, 1, 2, 3);
    expect(isEmpty(bounds)).toBe(false);
  });

  it('computes center, half extents and diagonal length of a unit-ish box', () => {
    const bounds = emptyAabb();
    expandByPoint(bounds, 0, 0, 0);
    expandByPoint(bounds, 2, 4, 6);

    expect(center(bounds)).toEqual([1, 2, 3]);
    expect(halfExtents(bounds)).toEqual([1, 2, 3]);
    expect(diagonalLength(bounds)).toBeCloseTo(Math.sqrt(4 + 16 + 36));
  });

  it('expands to cover another box, ignoring empty ones', () => {
    const a = emptyAabb();
    expandByPoint(a, 0, 0, 0);
    expandByPoint(a, 1, 1, 1);

    const b = emptyAabb();
    expandByPoint(b, 5, 5, 5);

    expandByAabb(a, b);
    expect(a.min).toEqual([0, 0, 0]);
    expect(a.max).toEqual([5, 5, 5]);

    const beforeEmptyMerge = { min: [...a.min], max: [...a.max] };
    expandByAabb(a, emptyAabb());
    expect(a).toEqual(beforeEmptyMerge);
  });
});
