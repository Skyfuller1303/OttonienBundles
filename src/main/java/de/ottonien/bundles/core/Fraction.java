package de.ottonien.bundles.core;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Immutable exact fraction on top of {@link BigInteger}. The denominator is always positive and the value is
 * always stored in lowest terms. All capacity decisions use this type, never floating point.
 */
public final class Fraction implements Comparable<Fraction> {

    public static final Fraction ZERO = new Fraction(BigInteger.ZERO, BigInteger.ONE);
    public static final Fraction ONE = new Fraction(BigInteger.ONE, BigInteger.ONE);

    private final BigInteger numerator;
    private final BigInteger denominator;

    private Fraction(BigInteger numerator, BigInteger denominator) {
        this.numerator = numerator;
        this.denominator = denominator;
    }

    public static Fraction of(long numerator, long denominator) {
        return of(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
    }

    public static Fraction of(BigInteger numerator, BigInteger denominator) {
        if (denominator.signum() == 0) {
            throw new ArithmeticException("Denominator must not be zero");
        }
        if (denominator.signum() < 0) {
            numerator = numerator.negate();
            denominator = denominator.negate();
        }
        BigInteger gcd = numerator.gcd(denominator);
        if (gcd.signum() != 0 && !gcd.equals(BigInteger.ONE)) {
            numerator = numerator.divide(gcd);
            denominator = denominator.divide(gcd);
        }
        return new Fraction(numerator, denominator);
    }

    public BigInteger numerator() {
        return numerator;
    }

    public BigInteger denominator() {
        return denominator;
    }

    public Fraction plus(Fraction other) {
        return of(numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),
                denominator.multiply(other.denominator));
    }

    public Fraction minus(Fraction other) {
        return of(numerator.multiply(other.denominator).subtract(other.numerator.multiply(denominator)),
                denominator.multiply(other.denominator));
    }

    public Fraction times(long factor) {
        return of(numerator.multiply(BigInteger.valueOf(factor)), denominator);
    }

    public Fraction dividedBy(long divisor) {
        return of(numerator, denominator.multiply(BigInteger.valueOf(divisor)));
    }

    /** Smallest integer that is not below this value. */
    public BigInteger ceil() {
        BigInteger[] qr = numerator.divideAndRemainder(denominator);
        return qr[1].signum() > 0 ? qr[0].add(BigInteger.ONE) : qr[0];
    }

    public boolean isInteger() {
        return denominator.equals(BigInteger.ONE);
    }

    public boolean isZero() {
        return numerator.signum() == 0;
    }

    public boolean isPositive() {
        return numerator.signum() > 0;
    }

    /** Largest integer n with n * unit <= this. Requires this >= 0 and unit > 0. */
    public BigInteger floorDivide(Fraction unit) {
        if (!unit.isPositive()) {
            throw new ArithmeticException("Unit must be positive");
        }
        if (numerator.signum() < 0) {
            throw new ArithmeticException("Dividend must not be negative");
        }
        return numerator.multiply(unit.denominator).divide(denominator.multiply(unit.numerator));
    }

    /** Percentage value (this * 100) for display only. */
    public BigDecimal toPercent(int scale) {
        return new BigDecimal(numerator.multiply(BigInteger.valueOf(100)))
                .divide(new BigDecimal(denominator), scale, RoundingMode.HALF_UP);
    }

    @Override
    public int compareTo(Fraction other) {
        return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Fraction f && numerator.equals(f.numerator) && denominator.equals(f.denominator);
    }

    @Override
    public int hashCode() {
        return numerator.hashCode() * 31 + denominator.hashCode();
    }

    @Override
    public String toString() {
        return denominator.equals(BigInteger.ONE) ? numerator.toString() : numerator + "/" + denominator;
    }
}
