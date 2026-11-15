package de.ottonien.bundles.core;

import org.junit.jupiter.api.Test;

import static de.ottonien.bundles.core.ExtractGesture.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ExtractGestureTest {

    @Test
    void rightClickWithBundleOnCursorIntoEmptySlot() {
        assertEquals(CURSOR_BUNDLE_GIVES_SLOT, classify(true, true, false, false, true));
    }

    @Test
    void rightClickOnBundleWithEmptyCursor() {
        assertEquals(SLOT_BUNDLE_GIVES_CURSOR, classify(true, false, true, true, false));
    }

    @Test
    void itemsOnBothSidesSwapAndNothingLeaves() {
        assertEquals(NONE, classify(true, true, false, true, false), "bundle on bundle");
        assertEquals(NONE, classify(true, true, false, false, false), "bundle on item");
        assertEquals(NONE, classify(true, false, false, true, false), "item on bundle");
    }

    @Test
    void leftClicksNeverTakeOut() {
        assertEquals(NONE, classify(false, true, false, false, true));
        assertEquals(NONE, classify(false, false, true, true, false));
    }

    @Test
    void clicksWithoutABundleAreIgnored() {
        assertEquals(NONE, classify(true, false, true, false, true));
        assertEquals(NONE, classify(true, false, false, false, true));
    }
}
