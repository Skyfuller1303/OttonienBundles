package de.ottonien.bundles.core;

/**
 * Decides whether a container click is the vanilla gesture that takes an entry out of a bundle. Mirrors the
 * {@code ClickAction.SECONDARY} branches of {@code BundleItem.overrideStackedOnOther} and
 * {@code BundleItem.overrideOtherStackedOnMe} in Minecraft 1.21.11: a plain right click with the bundle on
 * one side and nothing on the other. With items on both sides a right click swaps and nothing leaves.
 */
public enum ExtractGesture {

    /** Not a removal; vanilla handles the click. */
    NONE,
    /** The bundle on the cursor puts its selected entry into the empty clicked slot. */
    CURSOR_BUNDLE_GIVES_SLOT,
    /** The bundle in the clicked slot hands its selected entry to the empty cursor. */
    SLOT_BUNDLE_GIVES_CURSOR;

    public static ExtractGesture classify(boolean plainRightClick, boolean cursorIsBundle, boolean cursorEmpty,
                                          boolean slotIsBundle, boolean slotEmpty) {
        if (!plainRightClick) {
            return NONE;
        }
        if (cursorIsBundle && slotEmpty) {
            return CURSOR_BUNDLE_GIVES_SLOT;
        }
        if (slotIsBundle && cursorEmpty) {
            return SLOT_BUNDLE_GIVES_CURSOR;
        }
        return NONE;
    }
}
