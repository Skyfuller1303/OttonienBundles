package de.ottonien.bundles.core;

/**
 * Decides whether a container click is the vanilla gesture that puts an item into a bundle, and in which
 * direction. The rules mirror the server code of Minecraft 1.21.11
 * ({@code BundleItem.overrideStackedOnOther} and {@code BundleItem.overrideOtherStackedOnMe}):
 *
 * <ul>
 *   <li>Only a plain left click inserts ({@code ClickAction.PRIMARY}, sent as click type PICKUP with
 *       button 0). A right click on an item swaps or places, a right click on an empty counterpart takes
 *       one item out. Shift clicks, number keys, drops and double clicks never insert.</li>
 *   <li>Both sides must hold something.</li>
 *   <li>The bundle on the cursor is asked first. If the cursor holds a bundle it always consumes the click,
 *       even when the slot holds a bundle too.</li>
 * </ul>
 */
public enum InsertGesture {

    /** Not an insertion; vanilla handles the click. */
    NONE,
    /** The bundle on the cursor takes items out of the clicked slot. */
    CURSOR_BUNDLE_TAKES_SLOT,
    /** The bundle in the clicked slot takes the items on the cursor. */
    SLOT_BUNDLE_TAKES_CURSOR;

    /**
     * @param plainLeftClick true only for an unmodified left click on a slot
     * @param cursorIsBundle the cursor stack is a bundle
     * @param cursorEmpty    the cursor is empty
     * @param slotIsBundle   the stack in the clicked slot is a bundle
     * @param slotEmpty      the clicked slot is empty
     */
    public static InsertGesture classify(boolean plainLeftClick, boolean cursorIsBundle, boolean cursorEmpty,
                                         boolean slotIsBundle, boolean slotEmpty) {
        if (!plainLeftClick || cursorEmpty || slotEmpty) {
            return NONE;
        }
        if (cursorIsBundle) {
            return CURSOR_BUNDLE_TAKES_SLOT;
        }
        if (slotIsBundle) {
            return SLOT_BUNDLE_TAKES_CURSOR;
        }
        return NONE;
    }
}
