import { WorkbenchLayout } from '@scion/workbench';
import { describe, expect, it, vi } from 'vitest';
import { ADMIN_MENU_PART, syncAdminMenuPart } from './admin-menu-part';

/** Records the layout calls; each call returns the same fake so the chain can continue. */
function fakeLayout(hasAdminPart: boolean) {
  const layout = {
    hasPart: vi.fn((id: string) => id === ADMIN_MENU_PART && hasAdminPart),
    addPart: vi.fn(() => layout),
    navigatePart: vi.fn(() => layout),
    removePart: vi.fn(() => layout),
  };
  return layout;
}

function sync(layout: ReturnType<typeof fakeLayout>, isAdmin: boolean) {
  return syncAdminMenuPart(layout as unknown as WorkbenchLayout, isAdmin);
}

describe('syncAdminMenuPart', () => {
  it('adds and navigates the admin menu for an admin', () => {
    const layout = fakeLayout(false);

    expect(sync(layout, true)).toBe(layout);
    expect(layout.addPart).toHaveBeenCalledWith(
      ADMIN_MENU_PART,
      { dockTo: 'left-top' },
      expect.objectContaining({ label: '%menu.admin' }),
    );
    expect(layout.navigatePart).toHaveBeenCalledWith(ADMIN_MENU_PART, [ADMIN_MENU_PART]);
  });

  it('removes an admin menu persisted from an earlier admin session for a non-admin', () => {
    const layout = fakeLayout(true);

    expect(sync(layout, false)).toBe(layout);
    expect(layout.removePart).toHaveBeenCalledWith(ADMIN_MENU_PART);
  });

  it('leaves a layout that already matches the role alone', () => {
    const forAdmin = fakeLayout(true);
    const forNonAdmin = fakeLayout(false);

    expect(sync(forAdmin, true)).toBeNull();
    expect(sync(forNonAdmin, false)).toBeNull();
    expect(forAdmin.addPart).not.toHaveBeenCalled();
    expect(forNonAdmin.removePart).not.toHaveBeenCalled();
  });
});
