package de.ottonien.bundles.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static de.ottonien.bundles.core.TestStack.*;
import static org.junit.jupiter.api.Assertions.*;

class BundleRulesTest {

    private static final Map<String, Integer> DEFAULTS = Map.ofEntries(
            Map.entry("ottonien:erbsen", 4),
            Map.entry("ottonien:kohl", 4),
            Map.entry("ottonien:ruebe", 4),
            Map.entry("ottonien:erbsensuppe", 16),
            Map.entry("ottonien:gemuesesuppe", 16),
            Map.entry("ottonien:zwiebelsuppe", 16),
            Map.entry("ottonien:bratgemuese", 16),
            Map.entry("ottonien:eintopf", 32),
            Map.entry("ottonien:apfelkuchen", 64),
            Map.entry("ottonien:salz", 8));

    /** The shipped defaults at the vanilla bundle size; the expectation table of the briefing must hold. */
    private final BundleRules rules = new BundleRules(64, DEFAULTS);

    /** 16 single entries, as stored in bundle_contents (never one stack with count 16). */
    private static List<StackView> peas(int count) {
        return repeat(count, erbsen(1));
    }

    private static List<StackView> concat(List<StackView> a, List<StackView> b) {
        List<StackView> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    @Test
    void sixteenPeasFillBundleAndSeventeenthStaysOut() {
        List<StackView> contents = peas(16);
        assertEquals(Fraction.ONE, rules.occupancy(contents));
        assertEquals("100 %", Occupancy.formatPercent(rules.occupancy(contents)));
        assertEquals(0, rules.acceptableAmount(contents, erbsen(1)));
    }

    @Test
    void fifteenPeasLeaveRoomForExactlyOne() {
        assertEquals(1, rules.acceptableAmount(peas(15), erbsen(5)));
    }

    @Test
    void eightPeasPlusFourSaltAreExactlyFull() {
        List<StackView> contents = concat(peas(8), repeat(4, salz(1)));
        assertEquals(Fraction.ONE, rules.occupancy(contents));
        assertEquals(0, rules.acceptableAmount(contents, erbsen(1)));
    }

    @Test
    void eightPeasPlusTwoSoupsAreExactlyFull() {
        List<StackView> contents = concat(peas(8), repeat(2, erbsensuppe(1)));
        assertEquals(Fraction.ONE, rules.occupancy(contents));
    }

    @Test
    void sixPeasPlusTwoSaltLeaveSixSixteenthsFree() {
        List<StackView> contents = concat(peas(6), repeat(2, salz(1)));
        assertEquals(Fraction.of(10, 16), rules.occupancy(contents));
        assertEquals(Fraction.of(6, 16), rules.free(contents));
        assertEquals(6, rules.acceptableAmount(contents, erbsen(10)));
        assertEquals(3, rules.acceptableAmount(contents, salz(10)));
    }

    @Test
    void sixtyFourAmethystShardsOnlyHalfFitNextToEightPeas() {
        assertEquals(32, rules.acceptableAmount(peas(8), amethyst(64)));
    }

    @Test
    void plainAmethystKeepsVanillaOneSixtyFourthEach() {
        assertEquals(64, rules.acceptableAmount(List.of(), amethyst(64)));
        assertEquals(Fraction.of(1, 1), rules.resolve(amethyst(1)).weight());
        assertEquals(RuleKind.VANILLA, rules.resolve(amethyst(1)).kind());
    }

    @Test
    void saltWithStackSize64OffersAtMostEight() {
        TestStack salt64 = item(64, "ottonien:salz", 64);
        assertEquals(8, rules.acceptableAmount(List.of(), salt64));
        assertEquals(Fraction.of(8, 1), rules.resolve(salt64).weight());
        assertEquals(RuleKind.CONFIG, rules.resolve(salt64).kind());
    }

    @Test
    void singleItemsThatFillWholeBundle() {
        assertEquals(Fraction.ONE, rules.occupancy(List.of(apfelkuchen(1))));
        assertEquals(RuleKind.CONFIG, rules.resolve(apfelkuchen(1)).kind());
        assertEquals(Fraction.ONE, rules.occupancy(List.of(zwiebel(1))));
        assertEquals(RuleKind.VANILLA, rules.resolve(zwiebel(1)).kind());
        assertEquals(Fraction.ONE, rules.occupancy(List.of(ragout(1))));
        assertEquals(RuleKind.VANILLA, rules.resolve(ragout(1)).kind());
        assertEquals(0, rules.acceptableAmount(List.of(apfelkuchen(1)), erbsen(1)));
    }

    @Test
    void emptyInnerBundleOccupiesOneSixteenth() {
        assertEquals(Fraction.of(1, 16), rules.occupancy(List.of(bundle())));
    }

    @Test
    void innerBundleWithFourPeasOccupiesFiveSixteenths() {
        TestStack inner = bundle(peas(4).toArray(new StackView[0]));
        assertEquals(Fraction.of(5, 16), rules.occupancy(List.of(inner)));
        assertEquals(Fraction.of(20, 1), rules.resolve(inner).weight());
        assertEquals(RuleKind.NESTED_BUNDLE, rules.resolve(inner).kind());
    }

    @Test
    void doublyNestedBundleIsRecursive() {
        TestStack inner = bundle(peas(4).toArray(new StackView[0]));
        TestStack middle = bundle(inner);
        assertEquals(Fraction.of(24, 1), rules.resolve(middle).weight());
    }

    @Test
    void bundleCanBeOfferedIntoPartlyFilledBundle() {
        TestStack inner = bundle(peas(4).toArray(new StackView[0]));
        assertEquals(1, rules.acceptableAmount(peas(11), inner));
        assertEquals(0, rules.acceptableAmount(peas(12), inner));
    }

    @Test
    void reloadFromSixteenToEightKeepsContentsButBlocksInsertion() {
        BundleRules smaller = new BundleRules(64, Map.of("ottonien:erbsen", 8));
        List<StackView> contents = peas(16);
        assertEquals(Fraction.of(2, 1), smaller.occupancy(contents));
        assertEquals("200 %", Occupancy.formatPercent(smaller.occupancy(contents)));
        assertEquals(Fraction.ZERO, smaller.free(contents));
        assertEquals(0, smaller.acceptableAmount(contents, erbsen(1)));
        // still blocked at exactly full, free again once enough was removed
        assertEquals(0, smaller.acceptableAmount(peas(8), erbsen(1)));
        assertEquals(1, smaller.acceptableAmount(peas(7), erbsen(1)));
    }

    @Test
    void shulkerBoxStaysExcludedEvenWithConfigRule() {
        TestStack shulker = new TestStack(1, "ottonien:erbsen", false, false, List.of(), false, 1);
        assertFalse(rules.resolve(shulker).allowed());
        assertEquals(RuleKind.NOT_ALLOWED, rules.resolve(shulker).kind());
        assertEquals(0, rules.acceptableAmount(List.of(), shulker));
    }

    @Test
    void beehiveWithBeesFillsWholeBundleAndBeatsConfig() {
        TestStack hive = new TestStack(1, "ottonien:erbsen", true, false, List.of(), true, 64);
        assertEquals(Fraction.of(64, 1), rules.resolve(hive).weight());
        assertEquals(RuleKind.BEES, rules.resolve(hive).kind());
        assertEquals(0, rules.acceptableAmount(peas(1), hive));
        TestStack emptyHive = new TestStack(1, null, true, false, List.of(), false, 64);
        assertEquals(Fraction.of(1, 1), rules.resolve(emptyHive).weight());
    }

    @Test
    void customIdIsComparedExactlyWithoutNormalisation() {
        TestStack legacy = item(1, "erbsen", 1);
        assertEquals(RuleKind.VANILLA, rules.resolve(legacy).kind());
        assertEquals(Fraction.of(64, 1), rules.resolve(legacy).weight());
        assertFalse(rules.involvesConfigRule(legacy));
        BundleRules own = new BundleRules(64, Map.of("erbsen", 4));
        assertEquals(RuleKind.CONFIG, own.resolve(legacy).kind());
        assertEquals(RuleKind.VANILLA, own.resolve(erbsen(1)).kind());
    }

    @Test
    void itemModelIsSecondKeyAndPdcIdWins() {
        BundleRules own = new BundleRules(64, Map.of("ottonien:item/erbsen", 2, "ottonien:erbsen", 8));
        StackView modelOnly = withModel(item(1, null, 1), "ottonien:item/erbsen");
        assertEquals(RuleKind.CONFIG, own.resolve(modelOnly).kind());
        assertEquals(Fraction.of(2, 1), own.resolve(modelOnly).weight());
        assertTrue(own.involvesConfigRule(modelOnly));
        StackView both = withModel(erbsen(1), "ottonien:item/erbsen");
        assertEquals(Fraction.of(8, 1), own.resolve(both).weight());
        StackView unknownIdKnownModel = withModel(item(1, "erbsen", 1), "ottonien:item/erbsen");
        assertEquals(Fraction.of(2, 1), own.resolve(unknownIdKnownModel).weight());
        StackView otherModel = withModel(item(1, null, 1), "ottonien:item/kohl");
        assertEquals(RuleKind.VANILLA, own.resolve(otherModel).kind());
    }

    private static StackView withModel(TestStack base, String model) {
        return new StackView() {
            public int amount() { return base.amount(); }
            public String customId() { return base.customId(); }
            public String itemModel() { return model; }
            public boolean vanillaAllowed() { return base.vanillaAllowed(); }
            public boolean isBundle() { return base.isBundle(); }
            public List<StackView> bundleContents() { return base.bundleContents(); }
            public boolean hasBees() { return base.hasBees(); }
            public int effectiveMaxStackSize() { return base.effectiveMaxStackSize(); }
        };
    }

    @Test
    void modifiedStackSizeIsUsedForVanillaFallback() {
        assertEquals(Fraction.of(64, 99), rules.resolve(item(1, null, 99)).weight());
        assertEquals(Fraction.of(4, 1), rules.resolve(item(1, null, 16)).weight());
    }

    @Test
    void itemWithoutConfigEntryButWithIdFollowsVanilla() {
        assertEquals(RuleKind.VANILLA, rules.resolve(zwiebel(1)).kind());
    }

    @Test
    void involvesConfigRuleLooksIntoNestedBundles() {
        assertFalse(rules.involvesConfigRule(bundle()));
        assertFalse(rules.involvesConfigRule(bundle(amethyst(5))));
        assertTrue(rules.involvesConfigRule(bundle(erbsen(1))));
        assertTrue(rules.involvesConfigRule(bundle(bundle(salz(1)))));
    }

    @Test
    void insertionIsDecidedExactlyWithoutRounding() {
        // thirds cannot be represented in floating point; 3 of them must be exactly full
        BundleRules thirds = new BundleRules(3, Map.of("x", 1));
        List<StackView> two = repeat(2, item(1, "x", 1));
        assertEquals(1, thirds.acceptableAmount(two, item(5, "x", 1)));
        assertEquals(0, thirds.acceptableAmount(repeat(3, item(1, "x", 1)), item(1, "x", 1)));
        // stack sizes 99 and 98 cost 64/99 and 64/98 points; the sum must stay exact
        List<StackView> contents = List.of(item(1, null, 99), item(1, null, 98));
        assertTrue(rules.free(contents).compareTo(Fraction.ZERO) > 0);
        assertEquals(Fraction.of(99L * 98 - 98 - 99, 99L * 98), rules.free(contents));
        assertEquals(Fraction.of(64, 99).plus(Fraction.of(64, 98)), rules.points(contents));
        // 99 pieces of a stack-99 item are exactly 64 points
        assertEquals(Fraction.of(64, 1), rules.points(List.of(item(99, null, 99))));
        assertEquals(0, rules.acceptableAmount(List.of(item(99, null, 99)), item(1, null, 99)));
    }

    @Test
    void pointsAreReportedAlongsideTheShare() {
        List<StackView> contents = concat(peas(6), repeat(2, salz(1)));
        assertEquals(Fraction.of(40, 1), rules.points(contents));
        assertEquals(Fraction.of(24, 1), rules.freePoints(contents));
        assertFalse(rules.appliesToEveryBundle());
    }

    @Test
    void bundleSize128HoldsTwiceAsMuchOfEverything() {
        BundleRules big = new BundleRules(128, DEFAULTS);
        assertTrue(big.appliesToEveryBundle());
        TestStack stone = item(64, null, 64);
        TestStack sword = item(1, null, 1);
        assertEquals(64, big.acceptableAmount(List.of(), stone));
        assertEquals(64, big.acceptableAmount(List.of(stone), stone));
        assertEquals(Fraction.ONE, big.occupancy(List.of(stone, stone)));
        assertEquals(0, big.acceptableAmount(List.of(stone, stone), item(1, null, 64)));
        // 32 peas at 4 points each
        assertEquals(Fraction.ONE, big.occupancy(peas(32)));
        assertEquals(1, big.acceptableAmount(peas(31), erbsen(5)));
        assertEquals(0, big.acceptableAmount(peas(32), erbsen(1)));
        // two unstackable swords at 64 points each
        assertEquals(1, big.acceptableAmount(List.of(sword), sword));
        assertEquals(Fraction.ONE, big.occupancy(List.of(sword, sword)));
        assertEquals(0, big.acceptableAmount(List.of(sword, sword), sword));
        // mixed: one sword, 8 peas and 32 stones are exactly full
        List<StackView> mixed = concat(concat(List.of(sword), peas(8)), List.of(item(32, null, 64)));
        assertEquals(Fraction.of(128, 1), big.points(mixed));
        // special rules keep their point cost
        assertEquals(Fraction.of(4, 128), big.occupancy(List.of(bundle())));
        TestStack hive = new TestStack(1, null, true, false, List.of(), true, 64);
        assertEquals(Fraction.of(1, 2), big.occupancy(List.of(hive)));
        assertEquals(RuleKind.BEES, big.resolve(hive).kind());
    }

    @Test
    void smallerBundleSizeHoldsLess() {
        BundleRules small = new BundleRules(32, DEFAULTS);
        assertTrue(small.appliesToEveryBundle());
        assertEquals(32, small.acceptableAmount(List.of(), item(64, null, 64)));
        assertEquals(8, small.acceptableAmount(List.of(), erbsen(20)));
        assertEquals(Fraction.ONE, small.occupancy(peas(8)));
        // an unstackable item costs 64 points and never fits into 32
        assertEquals(0, small.acceptableAmount(List.of(), item(1, null, 1)));
        assertEquals(8, small.acceptableAmount(List.of(), item(16, null, 16)));
        // apple cake with size 64 is valid in the configuration but never fits
        assertEquals(0, small.acceptableAmount(List.of(), apfelkuchen(1)));
        assertEquals(7, small.acceptableAmount(List.of(bundle()), erbsen(20)));
    }

    @Test
    void sizeAboveBundleSizeNeverFits() {
        BundleRules r = new BundleRules(64, Map.of("big", 65));
        assertEquals(0, r.acceptableAmount(List.of(), item(1, "big", 64)));
        assertEquals(RuleKind.CONFIG, r.resolve(item(1, "big", 64)).kind());
    }

    @Test
    void emptyOrZeroOfferedAmountFitsNothing() {
        assertEquals(0, rules.acceptableAmount(List.of(), erbsen(0)));
    }

    @Test
    void veryDeepNestingTerminates() {
        TestStack b = bundle();
        for (int i = 0; i < 200; i++) {
            b = bundle(b);
        }
        assertTrue(rules.resolve(b).weight().compareTo(Fraction.ZERO) > 0);
    }

    @Test
    void percentFormatting() {
        assertEquals("62,5 %", Occupancy.formatPercent(Fraction.of(10, 16)));
        assertEquals("0 %", Occupancy.formatPercent(Fraction.ZERO));
        assertEquals("100 %", Occupancy.formatPercent(Fraction.ONE));
        assertEquals("33,3 %", Occupancy.formatPercent(Fraction.of(1, 3)));
        assertEquals("6,3 %", Occupancy.formatPercent(Fraction.of(1, 16)));
    }
}
