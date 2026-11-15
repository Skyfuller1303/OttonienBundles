package de.ottonien.bundles.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OccupancyTest {

    @Test
    void emptyBundleShowsNoCell() {
        assertEquals(0, Occupancy.filledCells(Fraction.ZERO, 16));
    }

    @Test
    void anyContentShowsAtLeastOneCell() {
        assertEquals(1, Occupancy.filledCells(Fraction.of(1, 96), 16));
    }

    @Test
    void cellsRoundDownSoTheBarIsFullOnlyWhenTheBundleIs() {
        assertEquals(8, Occupancy.filledCells(Fraction.of(1, 2), 16));
        assertEquals(10, Occupancy.filledCells(Fraction.of(2, 3), 16));
        assertEquals(15, Occupancy.filledCells(Fraction.of(95, 96), 16));
        assertEquals(16, Occupancy.filledCells(Fraction.ONE, 16));
    }

    @Test
    void overfilledBundleStaysAtAllCells() {
        assertEquals(16, Occupancy.filledCells(Fraction.of(2, 1), 16));
    }
}
