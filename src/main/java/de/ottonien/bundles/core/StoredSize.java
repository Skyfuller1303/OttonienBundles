package de.ottonien.bundles.core;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Chooses the stack size an item carries while it lies in a bundle, so that the weight the unmodified
 * client computes ({@code 1 / stack size} per piece) matches the share {@code cost / bundleSize} the piece
 * occupies.
 *
 * <p>Only divisors of {@value #DENOMINATOR_BASE} are used. Vanilla adds the weights of all entries with
 * 32-bit fractions and fails when the common denominator or a numerator leaves the int range (see
 * {@link VanillaWeight}). With every denominator dividing 960 the common denominator of any combination of
 * entries, nested bundles included, never exceeds 960.
 */
public final class StoredSize {

    /** Every stack size used inside a bundle divides this number (2^6 * 3 * 5). */
    public static final int DENOMINATOR_BASE = 960;

    /** Largest stack size the game allows. */
    public static final int MAX_STACK = 99;

    private static final int[] ALLOWED = allowed();

    private StoredSize() {
    }

    private static int[] allowed() {
        List<Integer> out = new ArrayList<>();
        for (int n = 1; n <= MAX_STACK; n++) {
            if (DENOMINATOR_BASE % n == 0) {
                out.add(n);
            }
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    /** The usable stack sizes in ascending order: 1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 16, ... 80, 96. */
    public static int[] allowedSizes() {
        return ALLOWED.clone();
    }

    public static boolean isAllowed(int stackSize) {
        return stackSize >= 1 && stackSize <= MAX_STACK && DENOMINATOR_BASE % stackSize == 0;
    }

    /**
     * The stack size for a piece that costs {@code cost} points in a bundle of {@code bundleSize} points.
     * The ideal value is {@code bundleSize / cost}. If that is not a usable size, the next larger usable
     * size is taken, so the client's bar shows less than the true fill and never reports "full" while the
     * server still has room. Only if the ideal value is above the largest usable size (96) the bar has to
     * overstate; a piece that does not fit at all gets 1.
     */
    public static int choose(Fraction cost, int bundleSize) {
        if (!cost.isPositive()) {
            throw new IllegalArgumentException("cost must be positive");
        }
        // smallest integer n with n >= bundleSize / cost
        BigInteger needed = Fraction.of(BigInteger.valueOf(bundleSize).multiply(cost.denominator()), cost.numerator())
                .ceil();
        for (int size : ALLOWED) {
            if (BigInteger.valueOf(size).compareTo(needed) >= 0) {
                return size;
            }
        }
        return ALLOWED[ALLOWED.length - 1];
    }

    /** True if the bar is exact for such a piece: {@code 1 / stackSize == cost / bundleSize}. */
    public static boolean isExact(Fraction cost, int bundleSize, int stackSize) {
        return Fraction.of(1, stackSize).equals(cost.dividedBy(bundleSize));
    }
}
