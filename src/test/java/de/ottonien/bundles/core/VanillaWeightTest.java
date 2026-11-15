package de.ottonien.bundles.core;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static de.ottonien.bundles.core.TestStack.*;
import static org.junit.jupiter.api.Assertions.*;

class VanillaWeightTest {

    /** An entry that carries {@code carried} as stack size inside the bundle. */
    private static StackView stored(int amount, int carried) {
        return item(amount, null, carried);
    }

    @Test
    void weightIsWhatVanillaComputes() {
        assertEquals(Fraction.ONE, VanillaWeight.weight(List.of(stored(16, 16))));
        assertEquals(Fraction.of(1, 2), VanillaWeight.weight(List.of(stored(32, 64))));
        assertEquals(Fraction.of(1, 16), VanillaWeight.weight(List.of(bundle())));
        assertEquals(Fraction.of(5, 16), VanillaWeight.weight(List.of(bundle(stored(4, 16)))));
        TestStack hive = new TestStack(1, null, true, false, List.of(), true, 64);
        assertEquals(Fraction.ONE, VanillaWeight.weight(List.of(hive)));
    }

    @Test
    void theLimitsLeaveNoRoomForAnIntOverflow() {
        long base = StoredSize.DENOMINATOR_BASE;
        // worst case: getMaxAmountToAdd of one such bundle against another
        assertTrue(base * base * VanillaWeight.MAX_WEIGHT <= Integer.MAX_VALUE);
        // against a single item of any stack size
        assertTrue(base * StoredSize.MAX_STACK * VanillaWeight.MAX_WEIGHT <= Integer.MAX_VALUE);
        // the common denominator of all usable sizes is the base itself
        BigInteger lcm = BigInteger.ONE;
        for (int size : StoredSize.allowedSizes()) {
            BigInteger s = BigInteger.valueOf(size);
            lcm = lcm.divide(lcm.gcd(s)).multiply(s);
        }
        assertEquals(BigInteger.valueOf(base), lcm);
    }

    @Test
    void theLargestBundleSizeStaysBelowTheWeightLimitEvenIfEveryPieceBecomesAWholeBundle() {
        // cheapest piece: stack size 99, 64/99 points; plus nested bundles at 4 points each
        long pieces = (long) BundleConfigParser.MAX_BUNDLE_SIZE * 99 / 64;
        long nested = BundleConfigParser.MAX_BUNDLE_SIZE / 4;
        assertTrue(pieces + nested / 16 + 1 <= VanillaWeight.MAX_WEIGHT);
    }

    @Test
    void usableSizesAreSafeInAnyMix() {
        List<StackView> entries = new ArrayList<>();
        for (int size : StoredSize.allowedSizes()) {
            entries.add(stored(1, size));
            entries.add(stored(size, size));
        }
        entries.add(bundle(stored(3, 96), bundle(stored(1, 5))));
        assertTrue(VanillaWeight.isSafe(entries));
    }

    @Test
    void foreignStackSizesAreRejected() {
        assertFalse(VanillaWeight.isSafe(List.of(stored(1, 99))));
        assertFalse(VanillaWeight.isSafe(List.of(stored(1, 97), stored(1, 64))));
        assertFalse(VanillaWeight.isSafe(List.of(bundle(bundle(stored(1, 7))))));
        // a bee container weighs a whole bundle whatever its stack size
        TestStack hive = new TestStack(1, null, true, false, List.of(), true, 99);
        assertTrue(VanillaWeight.isSafe(List.of(hive)));
    }

    @Test
    void excessiveWeightIsRejected() {
        List<StackView> entries = new ArrayList<>();
        for (int i = 0; i < VanillaWeight.MAX_WEIGHT; i++) {
            entries.add(stored(1, 1));
        }
        assertTrue(VanillaWeight.isSafe(entries));
        entries.add(stored(1, 96));
        assertFalse(VanillaWeight.isSafe(entries));
    }

    @Test
    void tooDeepNestingIsRejected() {
        TestStack b = bundle(stored(1, 64));
        for (int i = 0; i < BundleRules.MAX_DEPTH + 2; i++) {
            b = bundle(b);
        }
        assertFalse(VanillaWeight.isSafe(List.of(b)));
    }
}
