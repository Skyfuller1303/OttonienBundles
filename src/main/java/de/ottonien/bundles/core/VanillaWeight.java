package de.ottonien.bundles.core;

import java.util.List;

/**
 * Guards against the overflow in vanilla's own bundle weight.
 *
 * <p>{@code net.minecraft.world.item.component.BundleContents} keeps the sum of {@code count / stack size}
 * over all entries as an {@code org.apache.commons.lang3.math.Fraction}, which works on 32-bit integers
 * and throws an {@code ArithmeticException} on overflow. Verified on Paper 1.21.11 build 132 with entries
 * of stack sizes 97, 89, 83, 79, 73 and 71:
 *
 * <ul>
 *   <li>the constructor used by {@code CraftMetaBundle} and by the network codec throws,</li>
 *   <li>the storage codec answers "Excessive total bundle weight" and the whole bundle item fails to
 *       load,</li>
 *   <li>{@code Mutable.tryInsert} and {@code getMaxAmountToAdd} throw; CraftBukkit calls the latter while
 *       handling a click, before any event.</li>
 * </ul>
 *
 * <p>The plugin therefore only ever writes bundles that satisfy two conditions, checked by
 * {@link #isSafe}: every stack size inside (nested bundles included) divides
 * {@link StoredSize#DENOMINATOR_BASE} = 960, and the vanilla weight is at most {@value #MAX_WEIGHT}. Every
 * intermediate value vanilla computes is then bounded: sums and differences have a denominator dividing
 * 960 and a numerator of at most weight * 960; the division in {@code getMaxAmountToAdd} against another
 * such bundle produces at most 960 * 960 * weight = 1,843,200,000, which is below 2^31 - 1; against a
 * single item of any stack size up to 99 it produces at most 960 * 99 * weight.
 */
public final class VanillaWeight {

    /** Highest vanilla weight (1 = full for vanilla) of a bundle the plugin writes. */
    public static final int MAX_WEIGHT = 2000;

    private static final Fraction NESTED_BASE = Fraction.of(1, 16);

    private VanillaWeight() {
    }

    /** The weight vanilla and the client compute for these entries, as an exact fraction. */
    public static Fraction weight(List<? extends StackView> entries) {
        return weight(entries, 0);
    }

    private static Fraction weight(List<? extends StackView> entries, int depth) {
        Fraction sum = Fraction.ZERO;
        for (StackView entry : entries) {
            if (entry.amount() <= 0) {
                continue;
            }
            Fraction unit;
            if (entry.isBundle()) {
                Fraction inner = depth >= BundleRules.MAX_DEPTH ? Fraction.ZERO
                        : weight(entry.bundleContents(), depth + 1);
                unit = NESTED_BASE.plus(inner);
            } else if (entry.hasBees()) {
                unit = Fraction.ONE;
            } else {
                unit = Fraction.of(1, Math.max(1, entry.carriedMaxStackSize()));
            }
            sum = sum.plus(unit.times(entry.amount()));
        }
        return sum;
    }

    /** True if vanilla can handle a bundle with these entries without any risk of overflow. */
    public static boolean isSafe(List<? extends StackView> entries) {
        return denominatorsAllowed(entries, 0)
                && weight(entries).compareTo(Fraction.of(MAX_WEIGHT, 1)) <= 0;
    }

    private static boolean denominatorsAllowed(List<? extends StackView> entries, int depth) {
        for (StackView entry : entries) {
            if (entry.amount() <= 0) {
                continue;
            }
            if (entry.isBundle()) {
                if (depth >= BundleRules.MAX_DEPTH || !denominatorsAllowed(entry.bundleContents(), depth + 1)) {
                    return false;
                }
            } else if (!entry.hasBees() && !StoredSize.isAllowed(entry.carriedMaxStackSize())) {
                return false;
            }
        }
        return true;
    }
}
