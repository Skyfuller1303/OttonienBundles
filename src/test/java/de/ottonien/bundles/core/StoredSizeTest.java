package de.ottonien.bundles.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StoredSizeTest {

    private static int choose(long costNumerator, long costDenominator, int bundleSize) {
        return StoredSize.choose(Fraction.of(costNumerator, costDenominator), bundleSize);
    }

    @Test
    void allowedSizesAreExactlyTheDivisorsOf960UpTo99() {
        assertArrayEquals(new int[]{1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 16, 20, 24, 30, 32, 40, 48, 60, 64, 80, 96},
                StoredSize.allowedSizes());
        for (int size : StoredSize.allowedSizes()) {
            assertEquals(0, 960 % size);
            assertTrue(StoredSize.isAllowed(size));
        }
        assertFalse(StoredSize.isAllowed(99));
        assertFalse(StoredSize.isAllowed(7));
        assertFalse(StoredSize.isAllowed(0));
        assertFalse(StoredSize.isAllowed(120));
    }

    @Test
    void defaultsAtBundleSize64AreExact() {
        assertEquals(16, choose(4, 1, 64));   // peas
        assertEquals(8, choose(8, 1, 64));    // salt
        assertEquals(4, choose(16, 1, 64));   // soups
        assertEquals(2, choose(32, 1, 64));   // stew
        assertEquals(1, choose(64, 1, 64));   // apple cake
        assertTrue(StoredSize.isExact(Fraction.of(4, 1), 64, 16));
    }

    @Test
    void vanillaItemsAtBundleSize64KeepTheirOwnStackSize() {
        assertEquals(64, choose(1, 1, 64));   // stack 64 costs 1 point
        assertEquals(16, choose(4, 1, 64));   // stack 16 costs 4 points
        assertEquals(1, choose(64, 1, 64));   // unstackable costs 64 points
    }

    @Test
    void bundleSize128() {
        assertEquals(32, choose(4, 1, 128));  // peas: exact
        assertTrue(StoredSize.isExact(Fraction.of(4, 1), 128, 32));
        assertEquals(2, choose(64, 1, 128));  // sword: exact if it could be stacked
        // a stack-64 item would need 128, the largest usable size is 96: the bar overstates
        assertEquals(96, choose(1, 1, 128));
        assertFalse(StoredSize.isExact(Fraction.of(1, 1), 128, 96));
    }

    @Test
    void notRepresentableSharesRoundTowardsALargerStackSoTheBarNeverShowsFullEarly() {
        // ideal 64/9 = 7.1: next usable size is 8, so 1/8 <= 9/64
        assertEquals(8, choose(9, 1, 64));
        // ideal 7: not a divisor of 960, next is 8
        assertEquals(8, choose(1, 1, 7));
        // ideal 50: next is 60
        assertEquals(60, choose(2, 1, 100));
        for (int bundleSize : new int[]{1, 7, 32, 64, 100, 128, 1024}) {
            for (int cost = 1; cost <= 200; cost++) {
                Fraction costPoints = Fraction.of(cost, 1);
                int n = StoredSize.choose(costPoints, bundleSize);
                assertTrue(StoredSize.isAllowed(n));
                Fraction share = costPoints.dividedBy(bundleSize);
                boolean idealAboveLargest = Fraction.of(bundleSize, cost).compareTo(Fraction.of(96, 1)) > 0;
                boolean pieceNeverFits = cost > bundleSize;
                if (!idealAboveLargest && !pieceNeverFits) {
                    assertTrue(Fraction.of(1, n).compareTo(share) <= 0,
                            "bar must not overstate for cost " + cost + " in " + bundleSize);
                }
            }
        }
    }

    @Test
    void fractionalVanillaCostsAreHandled() {
        // stack 99 costs 64/99 points: ideal 99 is not usable and above 96
        assertEquals(96, choose(64, 99, 64));
        // stack 50 costs 64/50 points: ideal 50, next usable 60
        assertEquals(60, choose(64, 50, 64));
        // stack 10 costs 6.4 points: ideal 10 is usable and exact
        assertEquals(10, choose(64, 10, 64));
        assertTrue(StoredSize.isExact(Fraction.of(64, 10), 64, 10));
    }

    @Test
    void piecesThatFillOrExceedTheBundleGetOne() {
        assertEquals(1, choose(64, 1, 32));
        assertEquals(1, choose(1000000, 1, 64));
        assertEquals(1, choose(64, 1, 64));
    }

    @Test
    void nonPositiveCostIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> StoredSize.choose(Fraction.ZERO, 64));
    }
}
