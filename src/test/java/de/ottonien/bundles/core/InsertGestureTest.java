package de.ottonien.bundles.core;

import org.junit.jupiter.api.Test;

import static de.ottonien.bundles.core.InsertGesture.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class InsertGestureTest {

    @Test
    void leftClickWithBundleOnCursorTakesTheSlotItem() {
        assertEquals(CURSOR_BUNDLE_TAKES_SLOT, classify(true, true, false, false, false));
    }

    @Test
    void leftClickWithItemOnCursorFillsTheBundleInTheSlot() {
        assertEquals(SLOT_BUNDLE_TAKES_CURSOR, classify(true, false, false, true, false));
    }

    @Test
    void bundleOnCursorWinsWhenBothSidesAreBundles() {
        assertEquals(CURSOR_BUNDLE_TAKES_SLOT, classify(true, true, false, true, false));
    }

    /** Since 1.21.2 a right click swaps or takes out, it never inserts. */
    @Test
    void anythingButAPlainLeftClickIsLeftToVanilla() {
        assertEquals(NONE, classify(false, true, false, false, false));
        assertEquals(NONE, classify(false, false, false, true, false));
        assertEquals(NONE, classify(false, true, false, true, false));
    }

    @Test
    void emptyCounterpartIsNotAnInsertion() {
        assertEquals(NONE, classify(true, true, false, false, true), "bundle placed into an empty slot");
        assertEquals(NONE, classify(true, false, true, true, false), "bundle picked up with an empty cursor");
        assertEquals(NONE, classify(true, false, true, false, true));
    }

    @Test
    void clicksWithoutABundleAreIgnored() {
        assertEquals(NONE, classify(true, false, false, false, false));
    }
}
