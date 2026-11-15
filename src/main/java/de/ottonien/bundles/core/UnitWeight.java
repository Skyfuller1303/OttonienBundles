package de.ottonien.bundles.core;

/**
 * Result of rule resolution for one stack. {@code weight} is the cost of a single piece in points and is null
 * exactly when {@code kind} is {@link RuleKind#NOT_ALLOWED}. Points are exact fractions because the vanilla
 * cost {@code 64 / stack size} is not an integer for every stack size.
 */
public record UnitWeight(RuleKind kind, Fraction weight, String detail) {

    public boolean allowed() {
        return kind != RuleKind.NOT_ALLOWED;
    }
}
