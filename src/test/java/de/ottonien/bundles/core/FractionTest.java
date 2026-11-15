package de.ottonien.bundles.core;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

class FractionTest {

    @Test
    void normalisesAndCompares() {
        assertEquals(Fraction.of(1, 2), Fraction.of(8, 16));
        assertEquals(Fraction.of(-1, 2), Fraction.of(1, -2));
        assertTrue(Fraction.of(1, 3).compareTo(Fraction.of(2, 6)) == 0);
        assertTrue(Fraction.of(1, 99).compareTo(Fraction.of(1, 98)) < 0);
    }

    @Test
    void floorDivideIsExact() {
        assertEquals(BigInteger.valueOf(32), Fraction.of(1, 2).floorDivide(Fraction.of(1, 64)));
        assertEquals(BigInteger.valueOf(3), Fraction.of(6, 16).floorDivide(Fraction.of(1, 8)));
        assertEquals(BigInteger.ZERO, Fraction.of(1, 16).floorDivide(Fraction.of(1, 8)));
    }

    @Test
    void zeroDenominatorThrows() {
        assertThrows(ArithmeticException.class, () -> Fraction.of(1, 0));
    }
}
