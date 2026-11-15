package de.ottonien.bundles.core;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Display formatting for occupancy values. Display may round; decisions never use this class. */
public final class Occupancy {

    private Occupancy() {
    }

    /** German percent text with at most one decimal place, e.g. "62,5 %" or "100 %". */
    public static String formatPercent(Fraction occupancy) {
        BigDecimal percent = occupancy.toPercent(1).setScale(1, RoundingMode.HALF_UP);
        String text = percent.stripTrailingZeros().toPlainString().replace('.', ',');
        return text + " %";
    }

    /**
     * Number of filled cells of a text bar with {@code cells} cells. Rounds down, so the bar is completely
     * filled only when the bundle really is full (or overfilled), and shows at least one cell as soon as
     * anything is inside.
     */
    public static int filledCells(Fraction occupancy, int cells) {
        if (!occupancy.isPositive()) {
            return 0;
        }
        long filled = occupancy.times(cells).floorDivide(Fraction.ONE)
                .min(java.math.BigInteger.valueOf(cells)).longValue();
        return (int) Math.max(1, filled);
    }
}
