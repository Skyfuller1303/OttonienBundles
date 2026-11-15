package de.ottonien.bundles.core;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Exact capacity calculation for bundles in points. A bundle holds {@code bundleSize} points. Rule order:
 * vanilla admissibility, nested bundles and bee containers, configuration entry, vanilla cost.
 *
 * <p>With the vanilla bundle size of 64 one point is one item of a stack-64 item, so the vanilla cost of a
 * piece is {@code 64 / stack size} points, a nested bundle costs 4 points plus its contents and a bee
 * container with bees costs 64 points. These costs do not change with {@code bundleSize}; a larger bundle
 * simply holds more points.
 */
public final class BundleRules {

    /** Bundle size at which the point system equals vanilla. */
    public static final int VANILLA_BUNDLE_SIZE = 64;

    /** Recursion limit for nested bundles. Deeper nesting is treated as occupying the whole bundle. */
    public static final int MAX_DEPTH = 32;

    private static final Fraction NESTED_BUNDLE_POINTS = Fraction.of(4, 1);
    private static final Fraction BEES_POINTS = Fraction.of(64, 1);

    private final int bundleSize;
    private final Map<String, Integer> sizes;

    public BundleRules(int bundleSize, Map<String, Integer> sizes) {
        if (bundleSize < 1) {
            throw new IllegalArgumentException("bundleSize must be at least 1");
        }
        this.bundleSize = bundleSize;
        this.sizes = Map.copyOf(sizes);
    }

    /** Vanilla rules: bundle size 64 and no item entries. */
    public static BundleRules vanilla() {
        return new BundleRules(VANILLA_BUNDLE_SIZE, Map.of());
    }

    public int bundleSize() {
        return bundleSize;
    }

    /** Configured points per piece by item id. */
    public Map<String, Integer> sizes() {
        return sizes;
    }

    /**
     * True if the capacity differs from vanilla for every bundle, whatever it holds. With the vanilla
     * bundle size only bundles that involve a configured item are affected.
     */
    public boolean appliesToEveryBundle() {
        return bundleSize != VANILLA_BUNDLE_SIZE;
    }

    public UnitWeight resolve(StackView stack) {
        return resolve(stack, 0);
    }

    private UnitWeight resolve(StackView stack, int depth) {
        if (!stack.vanillaAllowed()) {
            return new UnitWeight(RuleKind.NOT_ALLOWED, null, "von Vanilla nicht zugelassen");
        }
        if (stack.isBundle()) {
            if (depth >= MAX_DEPTH) {
                return new UnitWeight(RuleKind.NESTED_BUNDLE, Fraction.of(bundleSize, 1), "Verschachtelung zu tief");
            }
            Fraction inner = points(stack.bundleContents(), depth + 1);
            return new UnitWeight(RuleKind.NESTED_BUNDLE, NESTED_BUNDLE_POINTS.plus(inner),
                    "Sonderregel Bündel: 4 Punkte + Inhalt " + inner);
        }
        if (stack.hasBees()) {
            return new UnitWeight(RuleKind.BEES, BEES_POINTS, "Sonderregel: enthält Bienen");
        }
        String key = configKey(stack);
        if (key != null) {
            int size = sizes.get(key);
            return new UnitWeight(RuleKind.CONFIG, Fraction.of(size, 1), "Eintrag '" + key + "' size " + size);
        }
        return vanillaCost(stack);
    }

    /**
     * Configuration key that governs the stack: the PDC ID if it has an entry, otherwise the explicitly set
     * item model if that has an entry, otherwise null.
     */
    public String configKey(StackView stack) {
        String id = stack.customId();
        if (id != null && sizes.containsKey(id)) {
            return id;
        }
        String model = stack.itemModel();
        if (model != null && sizes.containsKey(model)) {
            return model;
        }
        return null;
    }

    private static UnitWeight vanillaCost(StackView stack) {
        int max = Math.max(1, stack.effectiveMaxStackSize());
        return new UnitWeight(RuleKind.VANILLA, Fraction.of(VANILLA_BUNDLE_SIZE, max), "Vanilla, Stackgröße " + max);
    }

    /**
     * Exact number of points the given entries occupy. Entries that vanilla does not allow cannot be stored
     * by the plugin; if present anyway they are counted with their vanilla cost so nothing is hidden.
     */
    public Fraction points(List<? extends StackView> entries) {
        return points(entries, 0);
    }

    private Fraction points(List<? extends StackView> entries, int depth) {
        Fraction sum = Fraction.ZERO;
        for (StackView entry : entries) {
            if (entry.amount() <= 0) {
                continue;
            }
            UnitWeight w = resolve(entry, depth);
            Fraction unit = w.allowed() ? w.weight() : vanillaCost(entry).weight();
            sum = sum.plus(unit.times(entry.amount()));
        }
        return sum;
    }

    /** Occupied share of the bundle: points divided by the bundle size. 1 means exactly full. */
    public Fraction occupancy(List<? extends StackView> entries) {
        return points(entries).dividedBy(bundleSize);
    }

    /** Free points, never negative. */
    public Fraction freePoints(List<? extends StackView> contents) {
        Fraction free = Fraction.of(bundleSize, 1).minus(points(contents));
        return free.isPositive() ? free : Fraction.ZERO;
    }

    /** Free share of the bundle, never negative. */
    public Fraction free(List<? extends StackView> contents) {
        return freePoints(contents).dividedBy(bundleSize);
    }

    /**
     * Number of pieces of {@code offered} that fit into a bundle holding {@code contents}:
     * min(offered amount, floor(free points / points per piece)), at least 0.
     */
    public int acceptableAmount(List<? extends StackView> contents, StackView offered) {
        if (offered.amount() <= 0) {
            return 0;
        }
        UnitWeight w = resolve(offered);
        if (!w.allowed() || !w.weight().isPositive()) {
            return 0;
        }
        Fraction free = freePoints(contents);
        if (free.isZero()) {
            return 0;
        }
        BigInteger fit = free.floorDivide(w.weight());
        BigInteger offeredAmount = BigInteger.valueOf(offered.amount());
        return fit.min(offeredAmount).max(BigInteger.ZERO).intValueExact();
    }

    /** True if the stack, or anything stored in it, is governed by a configuration entry. */
    public boolean involvesConfigRule(StackView stack) {
        return involvesConfigRule(stack, 0);
    }

    private boolean involvesConfigRule(StackView stack, int depth) {
        if (stack.isBundle()) {
            if (depth >= MAX_DEPTH) {
                return false;
            }
            for (StackView inner : stack.bundleContents()) {
                if (involvesConfigRule(inner, depth + 1)) {
                    return true;
                }
            }
            return false;
        }
        return configKey(stack) != null;
    }
}
